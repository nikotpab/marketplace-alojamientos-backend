package com.inversioneslr.marketplace.reservas.infraestructura;

import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import com.inversioneslr.marketplace.reservas.dominio.AdicionalReservado;
import com.inversioneslr.marketplace.reservas.dominio.PeriodoNoDisponibleException;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import com.inversioneslr.marketplace.reservas.dominio.ResumenReserva;
import com.inversioneslr.marketplace.reservas.dominio.SolicitudProcesada;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
        return jdbc.sql("SELECT clave, huesped_id, reserva_id, resultado::text AS resultado, expira_en FROM solicitud_procesada WHERE clave = :clave")
                .param("clave", clave)
                .query((fila, numero) -> new SolicitudProcesada(
                        fila.getString("clave"),
                        fila.getObject("huesped_id", UUID.class),
                        fila.getObject("reserva_id", UUID.class),
                        fila.getString("resultado"),
                        fila.getTimestamp("expira_en").toInstant()))
                .optional();
    }

    @Override
    public boolean registrarSolicitud(String clave, UUID huespedId, Instant expiraEn) {
        return jdbc.sql("""
                INSERT INTO solicitud_procesada (clave, huesped_id, expira_en)
                VALUES (:clave, :huesped, :expiraEn)
                ON CONFLICT (clave) DO NOTHING""")
                .param("clave", clave)
                .param("huesped", huespedId)
                .param("expiraEn", Timestamp.from(expiraEn))
                .update() == 1;
    }

    @Override
    public void completarSolicitud(String clave, UUID reservaId, String resultado) {
        jdbc.sql("UPDATE solicitud_procesada SET reserva_id = :reserva, resultado = :resultado::jsonb WHERE clave = :clave")
                .param("reserva", reservaId)
                .param("resultado", resultado)
                .param("clave", clave)
                .update();
    }

    @Override
    public void eliminarSolicitudesVencidas(Instant ahora) {
        jdbc.sql("DELETE FROM solicitud_procesada WHERE expira_en < :ahora")
                .param("ahora", Timestamp.from(ahora))
                .update();
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
        return jdbc.sql("SELECT * FROM reserva WHERE id = :id")
                .param("id", id)
                .query((fila, numero) -> new Reserva(
                        id,
                        fila.getObject("huesped_id", UUID.class),
                        fila.getObject("publicacion_id", UUID.class),
                        fila.getObject("fecha_llegada", LocalDate.class),
                        fila.getObject("fecha_salida", LocalDate.class),
                        fila.getInt("numero_huespedes"),
                        EstadoReserva.valueOf(fila.getString("estado")),
                        fila.getInt("noches"),
                        fila.getBigDecimal("precio_base"),
                        fila.getBigDecimal("tarifa_limpieza"),
                        fila.getBigDecimal("tarifa_servicio"),
                        fila.getBigDecimal("descuento"),
                        fila.getBigDecimal("total")))
                .optional();
    }

    @Override
    public List<ResumenReserva> listarPorHuesped(UUID huespedId, EstadoReserva estado) {
        String sql = """
                SELECT r.id, r.huesped_id, r.publicacion_id, r.fecha_llegada, r.fecha_salida,
                       r.numero_huespedes, r.estado, r.noches, r.precio_base, r.tarifa_limpieza,
                       r.tarifa_servicio, r.descuento, r.total, p.titulo, p.ciudad
                FROM reserva r
                JOIN publicacion p ON p.id = r.publicacion_id
                WHERE r.huesped_id = :huespedId"""
                + (estado != null ? " AND r.estado = :estado" : "")
                + "\nORDER BY r.fecha_llegada DESC";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("huespedId", huespedId);
        if (estado != null) {
            spec = spec.param("estado", estado.name());
        }
        return spec.query((fila, num) -> mapResumen(fila, false)).list();
    }

    @Override
    public List<ResumenReserva> listarPorAnfitrion(UUID anfitrionId, EstadoReserva estado) {
        String sql = """
                SELECT r.id, r.huesped_id, r.publicacion_id, r.fecha_llegada, r.fecha_salida,
                       r.numero_huespedes, r.estado, r.noches, r.precio_base, r.tarifa_limpieza,
                       r.tarifa_servicio, r.descuento, r.total, p.titulo, p.ciudad,
                       u.nombre || ' ' || u.apellido AS nombre_huesped
                FROM reserva r
                JOIN publicacion p ON p.id = r.publicacion_id
                JOIN usuario u ON u.id = r.huesped_id
                WHERE p.anfitrion_id = :anfitrionId"""
                + (estado != null ? " AND r.estado = :estado" : "")
                + "\nORDER BY r.fecha_llegada DESC";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("anfitrionId", anfitrionId);
        if (estado != null) {
            spec = spec.param("estado", estado.name());
        }
        return spec.query((fila, num) -> mapResumen(fila, true)).list();
    }

    private static ResumenReserva mapResumen(ResultSet fila, boolean conNombre) throws SQLException {
        return new ResumenReserva(
                fila.getObject("id", UUID.class),
                fila.getObject("huesped_id", UUID.class),
                fila.getObject("publicacion_id", UUID.class),
                fila.getObject("fecha_llegada", LocalDate.class),
                fila.getObject("fecha_salida", LocalDate.class),
                fila.getInt("numero_huespedes"),
                EstadoReserva.valueOf(fila.getString("estado")),
                fila.getInt("noches"),
                fila.getBigDecimal("precio_base"),
                fila.getBigDecimal("tarifa_limpieza"),
                fila.getBigDecimal("tarifa_servicio"),
                fila.getBigDecimal("descuento"),
                fila.getBigDecimal("total"),
                fila.getString("titulo"),
                fila.getString("ciudad"),
                conNombre ? fila.getString("nombre_huesped") : null);
    }

    @Override
    public void actualizarEstado(UUID id, EstadoReserva estado) {
        jdbc.sql("UPDATE reserva SET estado = :estado WHERE id = :id")
                .param("estado", estado.name())
                .param("id", id)
                .update();
    }

    @Override
    public void marcarFuturasEnRevision(UUID publicacionId) {
        jdbc.sql("""
                UPDATE reserva SET estado = 'EN_REVISION'
                WHERE publicacion_id = :publicacionId
                  AND estado = 'CONFIRMADA'
                  AND fecha_llegada > CURRENT_DATE""")
                .param("publicacionId", publicacionId)
                .update();
    }
}
