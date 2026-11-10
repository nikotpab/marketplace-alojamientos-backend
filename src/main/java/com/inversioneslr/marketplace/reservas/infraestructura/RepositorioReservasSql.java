package com.inversioneslr.marketplace.reservas.infraestructura;

import com.inversioneslr.marketplace.reservas.EstadoReserva;
import com.inversioneslr.marketplace.reservas.dominio.AdicionalReservado;
import com.inversioneslr.marketplace.reservas.dominio.PeriodoNoDisponibleException;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import com.inversioneslr.marketplace.reservas.dominio.ReservaConAlojamiento;
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
    public Reserva guardar(Reserva reserva, List<AdicionalReservado> adicionales) {
        String codigo;
        try {
            codigo = jdbc.sql("""
                    INSERT INTO reserva (id, huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado,
                                         noches, precio_base, tarifa_limpieza, tarifa_servicio, descuento, total)
                    VALUES (:id, :huesped, :publicacion, :llegada, :salida, :huespedes, :estado,
                            :noches, :precioBase, :limpieza, :servicio, :descuento, :total)
                    RETURNING codigo""")
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
                    .query(String.class)
                    .single();
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
        return reserva.conCodigo(codigo);
    }

    @Override
    public Optional<Reserva> buscarPorId(UUID id) {
        return jdbc.sql("SELECT * FROM reserva WHERE id = :id")
                .param("id", id)
                .query(RepositorioReservasSql::aReserva)
                .optional();
    }

    @Override
    public List<ReservaConAlojamiento> listarPorHuesped(UUID huespedId, EstadoReserva estado) {
        return listar("r.huesped_id = :usuario", huespedId, estado);
    }

    @Override
    public List<ReservaConAlojamiento> listarPorAnfitrion(UUID anfitrionId, EstadoReserva estado) {
        return listar("p.anfitrion_id = :usuario", anfitrionId, estado);
    }

    private List<ReservaConAlojamiento> listar(String condicion, UUID usuarioId, EstadoReserva estado) {
        return jdbc.sql("""
                SELECT r.*, p.titulo AS alojamiento
                FROM reserva r
                JOIN publicacion p ON p.id = r.publicacion_id
                WHERE %s AND (CAST(:estado AS varchar) IS NULL OR r.estado = :estado)
                ORDER BY r.fecha_llegada DESC""".formatted(condicion))
                .param("usuario", usuarioId)
                .param("estado", estado == null ? null : estado.name())
                .query((fila, numero) -> new ReservaConAlojamiento(aReserva(fila, numero), fila.getString("alojamiento")))
                .list();
    }

    private static Reserva aReserva(ResultSet fila, int numero) throws SQLException {
        return new Reserva(
                fila.getObject("id", UUID.class),
                fila.getString("codigo"),
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
                fila.getBigDecimal("total"));
    }

    @Override
    public void actualizarEstado(UUID id, EstadoReserva estado) {
        jdbc.sql("UPDATE reserva SET estado = :estado WHERE id = :id")
                .param("estado", estado.name())
                .param("id", id)
                .update();
    }

    @Override
    public List<Reserva> listarConfirmadasConSalidaAntesDe(LocalDate dia) {
        return jdbc.sql("SELECT * FROM reserva WHERE estado = 'CONFIRMADA' AND fecha_salida < :dia")
                .param("dia", dia)
                .query(RepositorioReservasSql::aReserva)
                .list();
    }

    @Override
    public List<Reserva> marcarFuturasEnRevision(UUID publicacionId, LocalDate desde) {
        return jdbc.sql("""
                UPDATE reserva SET estado = 'EN_REVISION'
                WHERE publicacion_id = :publicacion AND estado = 'CONFIRMADA' AND fecha_llegada >= :desde
                RETURNING *""")
                .param("publicacion", publicacionId)
                .param("desde", desde)
                .query(RepositorioReservasSql::aReserva)
                .list();
    }
}
