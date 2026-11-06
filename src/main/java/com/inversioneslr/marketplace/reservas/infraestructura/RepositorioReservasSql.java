package com.inversioneslr.marketplace.reservas.infraestructura;

import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import com.inversioneslr.marketplace.reservas.dominio.AdicionalReservado;
import com.inversioneslr.marketplace.reservas.dominio.PeriodoNoDisponibleException;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import com.inversioneslr.marketplace.reservas.dominio.SolicitudProcesada;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioReservasSql implements RepositorioReservas {

    private static final String PENDIENTE = "SQL de reservas";
    private static final String RESTRICCION_ANTI_SOLAPE = "reserva_sin_solape";

    private final JdbcClient jdbc;

    public RepositorioReservasSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SolicitudProcesada> buscarSolicitud(String clave) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public void guardar(Reserva reserva, List<AdicionalReservado> adicionales) {
        try {
            jdbc.sql("""
                    INSERT INTO reserva (id, huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado,
                                         noches, precio_base, tarifa_limpieza, tarifa_servicio, descuento, total)
                    VALUES (:id, :huesped, :publicacion, :llegada, :salida, :huespedes, :estado,
                            :noches, :precioBase, :limpieza, :servicio, :descuento, :total)""")
                    .param("id", reserva.id())
                    .param("huesped", reserva.huespedId())
                    .param("publicacion", reserva.publicacionId())
                    .param("llegada", reserva.fechaLlegada())
                    .param("salida", reserva.fechaSalida())
                    .param("huespedes", reserva.numeroHuespedes())
                    .param("estado", reserva.estado().name())
                    .param("noches", reserva.noches())
                    .param("precioBase", reserva.precioBase())
                    .param("limpieza", reserva.tarifaLimpieza())
                    .param("servicio", reserva.tarifaServicio())
                    .param("descuento", reserva.descuento())
                    .param("total", reserva.total())
                    .update();
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMessage()).contains(RESTRICCION_ANTI_SOLAPE)) {
                throw new PeriodoNoDisponibleException();
            }
            throw e;
        }
        adicionales.forEach(adicional -> jdbc.sql("""
                INSERT INTO reserva_servicio_adicional (reserva_id, servicio_adicional_id, costo_aplicado)
                VALUES (:reserva, :adicional, :costo)""")
                .param("reserva", reserva.id())
                .param("adicional", adicional.servicioAdicionalId())
                .param("costo", adicional.costoAplicado())
                .update());
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
