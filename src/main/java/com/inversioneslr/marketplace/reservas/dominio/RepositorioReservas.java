package com.inversioneslr.marketplace.reservas.dominio;

import com.inversioneslr.marketplace.reservas.EstadoReserva;
import java.time.Instant;
import java.util.List;
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

    /** @throws PeriodoNoDisponibleException si la restricción anti-solape rechaza la reserva */
    /** @return la reserva con el código que le asignó la base de datos */
    Reserva guardar(Reserva reserva, List<AdicionalReservado> adicionales);

    Optional<Reserva> buscarPorId(UUID id);

    List<Reserva> listarPorHuesped(UUID huespedId, EstadoReserva estado);

    List<Reserva> listarPorAnfitrion(UUID anfitrionId, EstadoReserva estado);

    void actualizarEstado(UUID id, EstadoReserva estado);

    void marcarFuturasEnRevision(UUID publicacionId);
}
