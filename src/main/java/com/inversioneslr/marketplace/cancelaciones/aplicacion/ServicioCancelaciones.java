package com.inversioneslr.marketplace.cancelaciones.aplicacion;

import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion;
import com.inversioneslr.marketplace.cancelaciones.dominio.PoliticaCancelacion;
import com.inversioneslr.marketplace.cancelaciones.dominio.ReservaCancelable;
import com.inversioneslr.marketplace.cancelaciones.dominio.RepositorioCancelaciones;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.pagos.FachadaPagos;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import com.inversioneslr.marketplace.reservas.FachadaReservas;
import com.inversioneslr.marketplace.reservas.FachadaReservas.DatosReserva;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ServicioCancelaciones {

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
     * <li>En una sola transacción: reserva CANCELADA, cancelación, auditoría y evento.</li>
     * </ol>
     */
    public Cancelacion cancelarReserva(UUID huespedId, UUID reservaId, CancelarReservaComando comando) {
        throw new UnsupportedOperationException("CU-20");
    }

    public record EstimacionDevolucion(BigDecimal valorDevolucion, String politica) {
    }

    public record CancelarReservaComando(@NotBlank(message = "Indica el motivo de la cancelación.") String motivo) {
    }
}
