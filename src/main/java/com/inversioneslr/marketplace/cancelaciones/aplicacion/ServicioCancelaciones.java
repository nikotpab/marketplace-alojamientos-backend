package com.inversioneslr.marketplace.cancelaciones.aplicacion;

import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion;
import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion.EstadoFinal;
import com.inversioneslr.marketplace.cancelaciones.dominio.PoliticaCancelacion;
import com.inversioneslr.marketplace.cancelaciones.dominio.ReservaCancelable;
import com.inversioneslr.marketplace.cancelaciones.dominio.RepositorioCancelaciones;
import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.pagos.FachadaPagos;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import com.inversioneslr.marketplace.reservas.FachadaReservas;
import com.inversioneslr.marketplace.reservas.FachadaReservas.DatosReserva;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ServicioCancelaciones {

    private static final Logger log = LoggerFactory.getLogger(ServicioCancelaciones.class);

    private final RepositorioCancelaciones cancelaciones;
    private final FachadaReservas reservas;
    private final FachadaPublicaciones publicaciones;
    private final FachadaPagos pagos;
    private final PublicadorEventos eventos;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public ServicioCancelaciones(RepositorioCancelaciones cancelaciones, FachadaReservas reservas,
                                 FachadaPublicaciones publicaciones, FachadaPagos pagos, PublicadorEventos eventos,
                                 TransactionTemplate transaccion, Clock reloj) {
        this.cancelaciones = cancelaciones;
        this.reservas = reservas;
        this.publicaciones = publicaciones;
        this.pagos = pagos;
        this.eventos = eventos;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /** CU-20, paso 2: muestra la devolución antes de pedir la confirmación. */
    public EstimacionDevolucion estimarDevolucion(UUID huespedId, UUID reservaId) {
        DatosReserva reserva = reservaCancelable(huespedId, reservaId);
        String politica = publicaciones.datosPublicacion(reserva.publicacionId()).politicaCancelacion();
        return new EstimacionDevolucion(devolucion(reserva, politica), politica);
    }

    private DatosReserva reservaCancelable(UUID huespedId, UUID reservaId) {
        DatosReserva reserva = reservas.datosReserva(reservaId);
        if (!reserva.huespedId().equals(huespedId)) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.ACCESO_DENEGADO, "Esta reserva no es tuya.");
        }
        if (reserva.estado() != EstadoReserva.CONFIRMADA) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "Solo se puede cancelar una reserva confirmada.");
        }
        return reserva;
    }

    /** RF-23: según la política de la publicación y la fecha de hoy. */
    private BigDecimal devolucion(DatosReserva reserva, String politica) {
        ReservaCancelable cancelable = new ReservaCancelable(reserva.fechaLlegada(), reserva.total(), reserva.tarifaServicio());
        return PoliticaCancelacion.porNombre(politica).calcularDevolucion(cancelable, LocalDate.now(reloj));
    }

    /**
     * CU-20 · SEC-04.
     * <ol>
     * <li>Validar que la reserva sea del huésped y esté CONFIRMADA.</li>
     * <li>Calcular la devolución con la política de la publicación.</li>
     * <li>Solicitar la devolución fuera de la transacción.</li>
     * <li>En una sola transacción: reserva CANCELADA, cancelación, auditoría y eventos. Al dejar de estar CONFIRMADA,
     * la reserva libera el periodo (RF-13).</li>
     * </ol>
     * Si la devolución falla, la reserva se cancela igual con "devolución no procesada", y la falla queda en la
     * auditoría y se notifica al huésped (RF-23, pendiente 3 del README).
     */
    public Cancelacion cancelarReserva(UUID huespedId, UUID reservaId, CancelarReservaComando comando) {
        DatosReserva reserva = reservaCancelable(huespedId, reservaId);
        DatosPublicacion publicacion = publicaciones.datosPublicacion(reserva.publicacionId());
        BigDecimal valor = devolucion(reserva, publicacion.politicaCancelacion());
        EstadoFinal estadoFinal = devolver(reserva, valor);
        Cancelacion cancelacion = new Cancelacion(UUID.randomUUID(), reservaId, huespedId, reloj.instant(), comando.motivo(),
                valor, estadoFinal);
        return transaccion.execute(estado -> {
            reservas.marcarCancelada(reservaId);
            cancelaciones.guardar(cancelacion);
            eventos.publicar(new EventoDominio("ReservaCancelada", huespedId, "reserva", reservaId, estadoFinal.name(),
                    Map.of("motivo", comando.motivo(), "valorDevolucion", valor), List.of(huespedId, publicacion.anfitrionId())));
            if (estadoFinal == EstadoFinal.DEVOLUCION_PROCESADA) {
                eventos.publicar(new EventoDominio("DevolucionProcesada", huespedId, "reserva", reservaId, "EXITO",
                        Map.of("valorDevolucion", valor), List.of(huespedId)));
            } else if (estadoFinal == EstadoFinal.DEVOLUCION_NO_PROCESADA) {
                eventos.publicar(new EventoDominio("DevolucionFallida", huespedId, "reserva", reservaId, "FALLIDO",
                        Map.of("valorDevolucion", valor), List.of(huespedId)));
            }
            return cancelacion;
        });
    }

    /** Fuera de la transacción, para no retener una conexión mientras responde la pasarela. */
    private EstadoFinal devolver(DatosReserva reserva, BigDecimal valor) {
        if (valor.signum() == 0) {
            return EstadoFinal.SIN_DEVOLUCION;
        }
        try {
            pagos.devolverPagoDeReserva(reserva.id(), valor);
            return EstadoFinal.DEVOLUCION_PROCESADA;
        } catch (RuntimeException e) {
            log.error("No se pudo devolver el pago de la reserva {}", reserva.id(), e);
            return EstadoFinal.DEVOLUCION_NO_PROCESADA;
        }
    }

    public record EstimacionDevolucion(BigDecimal valorDevolucion, String politica) {
    }

    public record CancelarReservaComando(@NotBlank(message = "Indica el motivo de la cancelación.") String motivo) {
    }
}
