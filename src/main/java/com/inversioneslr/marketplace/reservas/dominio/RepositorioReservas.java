package com.inversioneslr.marketplace.reservas.dominio;

import com.inversioneslr.marketplace.reservas.EstadoReserva;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioReservas {

    Optional<SolicitudProcesada> buscarSolicitud(String clave);

    /**
     * Reserva la clave. Si otra transacción la está usando, espera a que termine.
     *
     * @return false si la clave ya estaba guardada
     */
    boolean registrarSolicitud(String clave, UUID huespedId, Instant expiraEn);

    /** @param reservaId null si la solicitud terminó rechazada */
    void completarSolicitud(String clave, UUID reservaId, String resultado);

    void eliminarSolicitudesVencidas(Instant ahora);

    /**
     * @return la reserva con el código que le asignó la base de datos
     * @throws PeriodoNoDisponibleException si la restricción anti-solape rechaza la reserva
     */
    Reserva guardar(Reserva reserva, List<AdicionalReservado> adicionales);

    Optional<Reserva> buscarPorId(UUID id);

    /**
     * De la llegada más reciente a la más antigua.
     *
     * @param estado null para todas
     */
    List<Reserva> listarPorHuesped(UUID huespedId, EstadoReserva estado);

    /** Las reservas de esas publicaciones, en el mismo orden. El título lo da la fachada de publicaciones (SCRUM-142). */
    List<Reserva> listarPorPublicaciones(Collection<UUID> publicacionIds, EstadoReserva estado);

    /** Las confirmadas de la publicación cuyas noches se cruzan con el rango semiabierto [desde, hasta). */
    List<Reserva> listarConfirmadasQueSeCruzan(UUID publicacionId, LocalDate desde, LocalDate hasta);

    /** Huésped de cada reserva de la publicación, en cualquier estado. */
    Map<UUID, UUID> huespedPorReserva(UUID publicacionId);

    void actualizarEstado(UUID id, EstadoReserva estado);

    /** Las confirmadas cuya fecha de salida es anterior a ese día. */
    List<Reserva> listarConfirmadasConSalidaAntesDe(LocalDate dia);

    /**
     * Pasa a EN_REVISION las confirmadas de la publicación que llegan ese día o después.
     *
     * @return las reservas que cambiaron
     */
    List<Reserva> marcarFuturasEnRevision(UUID publicacionId, LocalDate desde);
}
