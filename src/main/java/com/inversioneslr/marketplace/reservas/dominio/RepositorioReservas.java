package com.inversioneslr.marketplace.reservas.dominio;

import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioReservas {

    Optional<SolicitudProcesada> buscarSolicitud(String clave);

    /** @throws PeriodoNoDisponibleException si la restricción anti-solape rechaza la reserva */
    void guardar(Reserva reserva, List<AdicionalReservado> adicionales);

    Optional<Reserva> buscarPorId(UUID id);

    List<Reserva> listarPorHuesped(UUID huespedId, EstadoReserva estado);

    List<Reserva> listarPorAnfitrion(UUID anfitrionId, EstadoReserva estado);

    void actualizarEstado(UUID id, EstadoReserva estado);

    void marcarFuturasEnRevision(UUID publicacionId);
}
