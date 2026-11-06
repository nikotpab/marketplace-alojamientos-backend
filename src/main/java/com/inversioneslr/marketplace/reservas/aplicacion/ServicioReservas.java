package com.inversioneslr.marketplace.reservas.aplicacion;

import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import com.inversioneslr.marketplace.cotizacion.api.FachadaCotizacion;
import com.inversioneslr.marketplace.pagos.api.FachadaPagos;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones;
import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import com.inversioneslr.marketplace.reservas.api.FachadaReservas;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import com.inversioneslr.marketplace.usuarios.api.FachadaUsuarios;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ServicioReservas implements FachadaReservas {

    private final RepositorioReservas reservas;
    private final FachadaPublicaciones publicaciones;
    private final FachadaCotizacion cotizacion;
    private final FachadaPagos pagos;
    private final FachadaUsuarios usuarios;
    private final PublicadorEventos eventos;
    private final TransactionTemplate transaccion;

    public ServicioReservas(RepositorioReservas reservas, FachadaPublicaciones publicaciones, FachadaCotizacion cotizacion,
                            FachadaPagos pagos, FachadaUsuarios usuarios, PublicadorEventos eventos,
                            TransactionTemplate transaccion) {
        this.reservas = reservas;
        this.publicaciones = publicaciones;
        this.cotizacion = cotizacion;
        this.pagos = pagos;
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.transaccion = transaccion;
    }

    /**
     * CU-17 · SEC-01, SEC-02, SEC-03.
     * <ol>
     * <li>Si la clave ya se procesó, devolver el resultado guardado. Si es de otro huésped, denegar.</li>
     * <li>Validar publicación ACTIVA y recalcular la cotización.</li>
     * <li>Cobrar fuera de la transacción. Sin pago aprobado no se crea nada.</li>
     * <li>En una sola transacción: reserva CONFIRMADA, pago, clave con su resultado, auditoría y evento.</li>
     * <li>Si la restricción anti-solape rechaza: devolver el cobro y responder "periodo no disponible".</li>
     * <li>Si la clave ya existe (reintento simultáneo): devolver el resultado original sin devolver el cobro,
     * porque Stripe reutilizó el mismo pago.</li>
     * </ol>
     */
    public Reserva confirmarReserva(UUID huespedId, ConfirmarReservaComando comando, String claveSolicitud) {
        throw new UnsupportedOperationException("CU-17");
    }

    /** CU-18: el contacto del huésped solo llega al anfitrión si la reserva está confirmada (RNF-21). */
    public List<ReservaVista> consultarHistorial(UsuarioAutenticado usuario, EstadoReserva estado) {
        throw new UnsupportedOperationException("CU-18");
    }

    /** Pendiente RNF-12: completar o revertir en máximo 15 min las reservas que quedaron a medias. */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void reconciliarReservasIncompletas() {
    }

    /** Pendiente RF-25: pasar a COMPLETADA las reservas confirmadas cuya fecha de salida ya pasó. */
    @Scheduled(cron = "0 0 1 * * *", zone = "America/Bogota")
    public void completarReservasFinalizadas() {
    }

    @Override
    public DatosReserva datosReserva(UUID reservaId) {
        throw new UnsupportedOperationException("CU-20");
    }

    @Override
    public void marcarCancelada(UUID reservaId) {
        throw new UnsupportedOperationException("CU-20");
    }

    @Override
    public void marcarFuturasEnRevision(UUID publicacionId) {
        throw new UnsupportedOperationException("CU-24");
    }

    public record ConfirmarReservaComando(@NotNull UUID publicacionId,
                                          @NotNull LocalDate fechaLlegada,
                                          @NotNull LocalDate fechaSalida,
                                          @NotNull @Positive Integer numeroHuespedes,
                                          List<UUID> serviciosAdicionales) {
    }

    public record ReservaVista(Reserva reserva, FachadaUsuarios.DatosContacto contactoHuesped) {
    }
}
