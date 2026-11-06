package com.inversioneslr.marketplace.reservas.infraestructura;

import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import com.inversioneslr.marketplace.reservas.dominio.SolicitudProcesada;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioReservasSql implements RepositorioReservas {

    private static final String PENDIENTE = "SQL de reservas";

    private final JdbcClient jdbc;

    public RepositorioReservasSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SolicitudProcesada> buscarSolicitud(String clave) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public Reserva guardarConfirmacion(Reserva reserva, SolicitudProcesada solicitud) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public Optional<Reserva> buscarPorId(UUID id) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public List<Reserva> listarPorHuesped(UUID huespedId, EstadoReserva estado) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public List<Reserva> listarPorAnfitrion(UUID anfitrionId, EstadoReserva estado) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public void actualizarEstado(UUID id, EstadoReserva estado) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public void marcarFuturasEnRevision(UUID publicacionId) {
        throw new UnsupportedOperationException(PENDIENTE);
    }
}
