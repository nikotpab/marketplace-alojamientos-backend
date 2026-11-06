package com.inversioneslr.marketplace.reservas.aplicacion;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import com.inversioneslr.marketplace.cotizacion.api.Cotizacion;
import com.inversioneslr.marketplace.cotizacion.api.FachadaCotizacion;
import com.inversioneslr.marketplace.pagos.api.FachadaPagos;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import com.inversioneslr.marketplace.reservas.api.FachadaReservas;
import com.inversioneslr.marketplace.reservas.dominio.AdicionalReservado;
import com.inversioneslr.marketplace.reservas.dominio.PeriodoNoDisponibleException;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import com.inversioneslr.marketplace.reservas.dominio.SolicitudProcesada;
import com.inversioneslr.marketplace.usuarios.api.FachadaUsuarios;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ServicioReservas implements FachadaReservas {

    private static final Logger log = LoggerFactory.getLogger(ServicioReservas.class);

    /** Coincide con el mínimo que Stripe conserva sus claves de idempotencia. */
    private static final Duration RETENCION_CLAVE = Duration.ofHours(24);
    private static final String RESULTADO_RECHAZADO = "{\"rechazo\":\"PERIODO_NO_DISPONIBLE\"}";

    private final RepositorioReservas reservas;
    private final FachadaPublicaciones publicaciones;
    private final FachadaCotizacion cotizacion;
    private final FachadaPagos pagos;
    private final FachadaUsuarios usuarios;
    private final PublicadorEventos eventos;
    private final TransactionTemplate transaccion;
    private final Clock reloj;
    private final JsonMapper json;

    public ServicioReservas(RepositorioReservas reservas, FachadaPublicaciones publicaciones, FachadaCotizacion cotizacion,
                            FachadaPagos pagos, FachadaUsuarios usuarios, PublicadorEventos eventos,
                            TransactionTemplate transaccion, Clock reloj, JsonMapper json) {
        this.reservas = reservas;
        this.publicaciones = publicaciones;
        this.cotizacion = cotizacion;
        this.pagos = pagos;
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.transaccion = transaccion;
        this.reloj = reloj;
        this.json = json;
    }

    /**
     * CU-17 · SEC-01, SEC-02, SEC-03. Cobra fuera de la transacción para no retener una conexión mientras responde
     * la pasarela, y solo con el pago aprobado guarda la reserva, el pago, la clave, la auditoría y el evento, todo o nada.
     * Una solicitud repetida recibe el resultado original sin procesarse de nuevo (ADR-04).
     */
    public Reserva confirmarReserva(UUID huespedId, ConfirmarReservaComando comando, String claveSolicitud) {
        Optional<SolicitudProcesada> solicitudPrevia = reservas.buscarSolicitud(claveSolicitud);
        if (solicitudPrevia.isPresent()) {
            return resultadoOriginal(solicitudPrevia.get(), huespedId);
        }
        validarFechas(comando);
        DatosPublicacion publicacion = publicaciones.datosPublicacion(comando.publicacionId());
        validarPublicacion(publicacion, comando);
        if (!publicaciones.periodoDisponible(publicacion.id(), comando.fechaLlegada(), comando.fechaSalida())) {
            // Puede estar ocupado por esta misma solicitud, si un reintento simultáneo terminó primero.
            return reservas.buscarSolicitud(claveSolicitud)
                    .map(solicitud -> resultadoOriginal(solicitud, huespedId))
                    .orElseThrow(PeriodoNoDisponibleException::new);
        }
        List<UUID> seleccionados = comando.serviciosAdicionales() == null ? List.of() : comando.serviciosAdicionales();
        List<AdicionalReservado> adicionales = adicionalesSeleccionados(publicacion, seleccionados);
        Cotizacion cotizacionFinal = cotizacion.cotizar(publicacion.id(), comando.fechaLlegada(), comando.fechaSalida(), seleccionados);
        String referencia = cobrar(huespedId, publicacion, cotizacionFinal, claveSolicitud);
        Reserva reserva = nuevaReserva(huespedId, comando, cotizacionFinal);
        try {
            return transaccion.execute(estado -> {
                if (!reservas.registrarSolicitud(claveSolicitud, huespedId, reloj.instant().plus(RETENCION_CLAVE))) {
                    // Reintento simultáneo: comparte el pago con la solicitud ganadora, así que no se devuelve.
                    return resultadoOriginal(reservas.buscarSolicitud(claveSolicitud).orElseThrow(), huespedId);
                }
                reservas.guardar(reserva, adicionales);
                pagos.registrarPago(reserva.id(), referencia, claveSolicitud, reserva.total());
                eventos.publicar(new EventoDominio("ReservaConfirmada", huespedId, "reserva", reserva.id(), "EXITO",
                        Map.of("total", reserva.total()), List.of(huespedId, publicacion.anfitrionId())));
                reservas.completarSolicitud(claveSolicitud, reserva.id(), json.writeValueAsString(reserva));
                return reserva;
            });
        } catch (PeriodoNoDisponibleException otraReservaGano) {
            devolverCobro(huespedId, publicacion, referencia, reserva);
            recordarRechazo(claveSolicitud, huespedId);
            throw otraReservaGano;
        }
    }

    private Reserva resultadoOriginal(SolicitudProcesada solicitud, UUID huespedId) {
        if (!solicitud.huespedId().equals(huespedId)) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.ACCESO_DENEGADO, "Esta solicitud pertenece a otro huésped.");
        }
        if (solicitud.reservaId() == null) {
            throw new PeriodoNoDisponibleException();
        }
        return json.readValue(solicitud.resultado(), Reserva.class);
    }

    /** Sin esto, un reintento volvería a cobrar con la misma clave un pago que ya se devolvió. */
    private void recordarRechazo(String claveSolicitud, UUID huespedId) {
        transaccion.executeWithoutResult(estado -> {
            if (reservas.registrarSolicitud(claveSolicitud, huespedId, reloj.instant().plus(RETENCION_CLAVE))) {
                reservas.completarSolicitud(claveSolicitud, null, RESULTADO_RECHAZADO);
            }
        });
    }

    /** SEC-03: otra reserva ganó el periodo. Si la devolución falla, queda en auditoría para reconciliarla (SCRUM-59). */
    private void devolverCobro(UUID huespedId, DatosPublicacion publicacion, String referencia, Reserva reserva) {
        try {
            pagos.devolver(referencia, reserva.total());
        } catch (ExcepcionNegocio error) {
            log.error("No se pudo devolver el cobro {} tras perder el periodo", referencia);
            eventos.publicar(new EventoDominio("DevolucionFallida", huespedId, "publicacion", publicacion.id(), error.tipo().name(),
                    Map.of("referencia", referencia, "total", reserva.total()), List.of()));
        }
    }

    private void validarFechas(ConfirmarReservaComando comando) {
        if (!comando.fechaSalida().isAfter(comando.fechaLlegada())) {
            throw datoInvalido("fechaSalida", "La salida debe ser posterior a la llegada. Elige una fecha de salida más tardía.");
        }
        if (comando.fechaLlegada().isBefore(LocalDate.now(reloj))) {
            throw datoInvalido("fechaLlegada", "La llegada ya pasó. Elige una fecha de hoy en adelante.");
        }
    }

    private static void validarPublicacion(DatosPublicacion publicacion, ConfirmarReservaComando comando) {
        if (!publicacion.activa()) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "El alojamiento no está disponible.");
        }
        if (comando.numeroHuespedes() > publicacion.capacidad()) {
            throw datoInvalido("numeroHuespedes", "El alojamiento admite hasta " + publicacion.capacidad() + " huéspedes. Reduce el número.");
        }
    }

    private static List<AdicionalReservado> adicionalesSeleccionados(DatosPublicacion publicacion, List<UUID> seleccionados) {
        List<AdicionalReservado> adicionales = publicacion.serviciosAdicionales().stream()
                .filter(adicional -> seleccionados.contains(adicional.id()))
                .map(adicional -> new AdicionalReservado(adicional.id(), adicional.costo()))
                .toList();
        if (adicionales.size() != seleccionados.size()) {
            throw datoInvalido("serviciosAdicionales", "Algún servicio adicional no pertenece a este alojamiento. Elige solo los que ofrece.");
        }
        return adicionales;
    }

    /** SEC-02: un pago rechazado o no procesado no crea nada, pero la falla queda en auditoría. */
    private String cobrar(UUID huespedId, DatosPublicacion publicacion, Cotizacion cotizacionFinal, String claveSolicitud) {
        try {
            return pagos.cobrar(cotizacionFinal.total(), claveSolicitud);
        } catch (ExcepcionNegocio error) {
            eventos.publicar(new EventoDominio("PagoFallido", huespedId, "publicacion", publicacion.id(), error.tipo().name(),
                    Map.of("total", cotizacionFinal.total()), List.of()));
            throw error;
        }
    }

    private static Reserva nuevaReserva(UUID huespedId, ConfirmarReservaComando comando, Cotizacion cotizacionFinal) {
        return new Reserva(UUID.randomUUID(), huespedId, comando.publicacionId(), comando.fechaLlegada(), comando.fechaSalida(),
                comando.numeroHuespedes(), EstadoReserva.CONFIRMADA, cotizacionFinal.noches(), cotizacionFinal.precioBase(),
                cotizacionFinal.tarifaLimpieza(), cotizacionFinal.tarifaServicio(), cotizacionFinal.descuento(), cotizacionFinal.total());
    }

    private static ExcepcionNegocio datoInvalido(String campo, String mensaje) {
        return new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, mensaje, Map.of(campo, mensaje));
    }

    /** CU-18: el contacto del huésped solo llega al anfitrión si la reserva está confirmada (RNF-21). */
    public List<ReservaVista> consultarHistorial(UsuarioAutenticado usuario, EstadoReserva estado) {
        throw new UnsupportedOperationException("CU-18");
    }

    /** ADR-04: las claves se conservan 24 h, tiempo suficiente para los reintentos de un cliente. */
    @Scheduled(cron = "0 0 * * * *")
    public void eliminarSolicitudesVencidas() {
        reservas.eliminarSolicitudesVencidas(reloj.instant());
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
        Reserva reserva = buscar(reservaId);
        return new DatosReserva(reserva.id(), reserva.huespedId(), reserva.publicacionId(), reserva.fechaLlegada(),
                reserva.estado(), reserva.total(), reserva.tarifaServicio());
    }

    private Reserva buscar(UUID reservaId) {
        return reservas.buscarPorId(reservaId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "La reserva no existe."));
    }

    @Override
    public void marcarCancelada(UUID reservaId) {
        reservas.actualizarEstado(reservaId, buscar(reservaId).cancelar().estado());
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
