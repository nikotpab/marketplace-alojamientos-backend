package com.inversioneslr.marketplace.reservas.aplicacion;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicacionId;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.ReservaId;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import com.inversioneslr.marketplace.compartido.UsuarioId;
import com.inversioneslr.marketplace.cotizacion.Cotizacion;
import com.inversioneslr.marketplace.cotizacion.FachadaCotizacion;
import com.inversioneslr.marketplace.pagos.FachadaPagos;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import com.inversioneslr.marketplace.reservas.EventosReservas.DevolucionFallida;
import com.inversioneslr.marketplace.reservas.EventosReservas.PagoFallido;
import com.inversioneslr.marketplace.reservas.EventosReservas.ReservaCompletada;
import com.inversioneslr.marketplace.reservas.EventosReservas.ReservaConfirmada;
import com.inversioneslr.marketplace.reservas.EventosReservas.ReservaEnRevision;
import com.inversioneslr.marketplace.reservas.FachadaReservas;
import com.inversioneslr.marketplace.reservas.dominio.AdicionalReservado;
import com.inversioneslr.marketplace.reservas.dominio.PeriodoNoDisponibleException;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import com.inversioneslr.marketplace.reservas.dominio.SolicitudProcesada;
import com.inversioneslr.marketplace.usuarios.FachadaUsuarios;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
        DatosPublicacion publicacion = publicaciones.datosPublicacion(new PublicacionId(comando.publicacionId()));
        validarPublicacion(publicacion, comando);
        if (!publicaciones.periodoDisponible(new PublicacionId(publicacion.id()), comando.llegada(), comando.salida())) {
            // Puede estar ocupado por esta misma solicitud, si un reintento simultáneo terminó primero.
            return reservas.buscarSolicitud(claveSolicitud)
                    .map(solicitud -> resultadoOriginal(solicitud, huespedId))
                    .orElseThrow(PeriodoNoDisponibleException::new);
        }
        List<UUID> seleccionados = comando.adicionales() == null ? List.of() : comando.adicionales();
        List<AdicionalReservado> adicionales = adicionalesSeleccionados(publicacion, seleccionados);
        Cotizacion cotizacionFinal = cotizacion.cotizar(new PublicacionId(publicacion.id()), comando.llegada(), comando.salida(), seleccionados);
        String referencia = cobrar(huespedId, publicacion, cotizacionFinal, claveSolicitud);
        Reserva reserva = nuevaReserva(huespedId, comando, cotizacionFinal);
        try {
            return transaccion.execute(estado -> {
                if (!reservas.registrarSolicitud(claveSolicitud, huespedId, reloj.instant().plus(RETENCION_CLAVE))) {
                    // Reintento simultáneo: comparte el pago con la solicitud ganadora, así que no se devuelve.
                    return resultadoOriginal(reservas.buscarSolicitud(claveSolicitud).orElseThrow(), huespedId);
                }
                Reserva guardada = reservas.guardar(reserva, adicionales);
                pagos.registrarPago(new ReservaId(guardada.id()), referencia, claveSolicitud, guardada.total());
                eventos.publicar(new ReservaConfirmada(huespedId, guardada.id(), guardada.total(), guardada.codigo(), publicacion.anfitrionId()));
                reservas.completarSolicitud(claveSolicitud, guardada.id(), json.writeValueAsString(guardada));
                return guardada;
            });
        } catch (PeriodoNoDisponibleException otraReservaGano) {
            devolverCobro(huespedId, publicacion, claveSolicitud, referencia, reserva);
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

    /** SEC-03: otra reserva ganó el periodo. Si la devolución falla, queda en auditoría y la reconciliación la reintenta. */
    private void devolverCobro(UUID huespedId, DatosPublicacion publicacion, String claveSolicitud, String referencia, Reserva reserva) {
        try {
            pagos.devolverCobroSinReserva(claveSolicitud, referencia, reserva.total());
        } catch (ExcepcionNegocio error) {
            log.error("No se pudo devolver el cobro {} tras perder el periodo", referencia);
            eventos.publicar(new DevolucionFallida(huespedId, publicacion.id(), error.tipo().name(), referencia, reserva.total()));
        }
    }

    private void validarFechas(ConfirmarReservaComando comando) {
        if (!comando.salida().isAfter(comando.llegada())) {
            throw datoInvalido("salida", "La salida debe ser posterior a la llegada. Elige una fecha de salida más tardía.");
        }
        if (comando.llegada().isBefore(LocalDate.now(reloj))) {
            throw datoInvalido("llegada", "La llegada ya pasó. Elige una fecha de hoy en adelante.");
        }
    }

    private static void validarPublicacion(DatosPublicacion publicacion, ConfirmarReservaComando comando) {
        if (!publicacion.activa()) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "El alojamiento no está disponible.");
        }
        if (comando.huespedes() > publicacion.capacidad()) {
            throw datoInvalido("huespedes", "El alojamiento admite hasta " + publicacion.capacidad() + " huéspedes. Reduce el número.");
        }
    }

    private static List<AdicionalReservado> adicionalesSeleccionados(DatosPublicacion publicacion, List<UUID> seleccionados) {
        List<AdicionalReservado> adicionales = publicacion.serviciosAdicionales().stream()
                .filter(adicional -> seleccionados.contains(adicional.id()))
                .map(adicional -> new AdicionalReservado(adicional.id(), adicional.costo()))
                .toList();
        if (adicionales.size() != seleccionados.size()) {
            throw datoInvalido("adicionales", "Algún servicio adicional no pertenece a este alojamiento. Elige solo los que ofrece.");
        }
        return adicionales;
    }

    /** SEC-02: un pago rechazado o no procesado no crea nada, pero la falla queda en auditoría. */
    private String cobrar(UUID huespedId, DatosPublicacion publicacion, Cotizacion cotizacionFinal, String claveSolicitud) {
        try {
            return pagos.cobrar(cotizacionFinal.total(), claveSolicitud);
        } catch (ExcepcionNegocio error) {
            eventos.publicar(new PagoFallido(huespedId, publicacion.id(), error.tipo().name(), cotizacionFinal.total()));
            throw error;
        }
    }

    private static Reserva nuevaReserva(UUID huespedId, ConfirmarReservaComando comando, Cotizacion cotizacionFinal) {
        return new Reserva(UUID.randomUUID(), null, huespedId, comando.publicacionId(), comando.llegada(), comando.salida(),
                comando.huespedes(), EstadoReserva.CONFIRMADA, cotizacionFinal.noches(), cotizacionFinal.precioBase(),
                cotizacionFinal.tarifaLimpieza(), cotizacionFinal.tarifaServicio(), cotizacionFinal.descuento(), cotizacionFinal.total());
    }

    private static ExcepcionNegocio datoInvalido(String campo, String mensaje) {
        return new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, mensaje, Map.of(campo, mensaje));
    }

    /**
     * CU-18: el huésped ve sus reservas y el anfitrión las de sus alojamientos. El contacto del huésped solo llega al
     * anfitrión, y solo si la reserva está confirmada (RNF-21).
     */
    public List<ReservaVista> consultarHistorial(UsuarioAutenticado usuario, EstadoReserva estado) {
        boolean anfitrion = usuario.rol() == Rol.ANFITRION;
        List<Reserva> listadas = anfitrion
                ? reservas.listarPorPublicaciones(publicaciones.publicacionesDelAnfitrion(new UsuarioId(usuario.id())).stream()
                        .map(PublicacionId::valor).toList(), estado)
                : reservas.listarPorHuesped(usuario.id(), estado);
        // SCRUM-142: los títulos en una sola consulta a la fachada de publicaciones, sin leer su tabla
        Map<PublicacionId, String> alojamientos = publicaciones.titulos(listadas.stream().map(reserva -> new PublicacionId(reserva.publicacionId())).toList());
        return listadas.stream()
                .map(reserva -> ReservaVista.de(reserva, alojamientos.get(new PublicacionId(reserva.publicacionId())),
                        anfitrion && reserva.estado() == EstadoReserva.CONFIRMADA ? usuarios.datosContacto(new UsuarioId(reserva.huespedId())) : null))
                .toList();
    }

    /** ADR-04: las claves se conservan 24 h, tiempo suficiente para los reintentos de un cliente. */
    @Scheduled(cron = "0 0 * * * *")
    public void eliminarSolicitudesVencidas() {
        reservas.eliminarSolicitudesVencidas(reloj.instant());
    }

    /**
     * RF-25 · pendiente 13 del README: cada noche pasa a COMPLETADA las reservas confirmadas cuya fecha de salida ya
     * pasó, y avisa al huésped de que ya puede reseñar.
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "America/Bogota")
    public void completarReservasFinalizadas() {
        reservas.listarConfirmadasConSalidaAntesDe(LocalDate.now(reloj)).forEach(reserva -> transaccion.executeWithoutResult(estado -> {
            reservas.actualizarEstado(reserva.id(), reserva.completar().estado());
            eventos.publicar(new ReservaCompletada(reserva.id(), reserva.huespedId()));
        }));
    }

    @Override
    public DatosReserva datosReserva(ReservaId reservaId) {
        Reserva reserva = buscar(reservaId.valor());
        return new DatosReserva(reserva.id(), reserva.huespedId(), reserva.publicacionId(), reserva.fechaLlegada(),
                reserva.estado(), reserva.total(), reserva.tarifaServicio());
    }

    private Reserva buscar(UUID reservaId) {
        return reservas.buscarPorId(reservaId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "La reserva no existe."));
    }

    @Override
    public void marcarCancelada(ReservaId reservaId) {
        reservas.actualizarEstado(reservaId.valor(), buscar(reservaId.valor()).cancelar().estado());
    }

    /** CU-24: las que aún no empiezan se conservan para auditoría y pasan a revisión manual. Se avisa a cada huésped. */
    @Override
    public void marcarFuturasEnRevision(PublicacionId publicacionId) {
        reservas.marcarFuturasEnRevision(publicacionId.valor(), LocalDate.now(reloj)).forEach(reserva ->
                eventos.publicar(new ReservaEnRevision(reserva.id(), publicacionId.valor(), reserva.huespedId())));
    }

    @Override
    public Map<ReservaId, UsuarioId> huespedPorReserva(PublicacionId publicacionId) {
        return reservas.huespedPorReserva(publicacionId.valor()).entrySet().stream()
                .collect(Collectors.toMap(fila -> new ReservaId(fila.getKey()), fila -> new UsuarioId(fila.getValue())));
    }

    /** Con los nombres que envía el frontend. */
    public record ConfirmarReservaComando(@NotNull UUID publicacionId,
                                          @NotNull LocalDate llegada,
                                          @NotNull LocalDate salida,
                                          @NotNull @Positive Integer huespedes,
                                          List<UUID> adicionales) {
    }

    /** Con los nombres del frontend. El frontend agrupa por estado: próximas, completadas y canceladas. */
    public record ReservaVista(UUID id, String codigo, EstadoReserva estado, UUID publicacionId, String alojamiento,
                               LocalDate llegada, LocalDate salida, int huespedes, int noches, BigDecimal total,
                               FachadaUsuarios.DatosContacto contactoHuesped) {

        static ReservaVista de(Reserva reserva, String alojamiento, FachadaUsuarios.DatosContacto contacto) {
            return new ReservaVista(reserva.id(), reserva.codigo(), reserva.estado(), reserva.publicacionId(), alojamiento,
                    reserva.fechaLlegada(), reserva.fechaSalida(), reserva.numeroHuespedes(), reserva.noches(), reserva.total(), contacto);
        }
    }
}
