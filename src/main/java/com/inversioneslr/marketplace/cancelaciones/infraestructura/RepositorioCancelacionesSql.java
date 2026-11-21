package com.inversioneslr.marketplace.cancelaciones.infraestructura;

import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion;
import com.inversioneslr.marketplace.cancelaciones.dominio.RepositorioCancelaciones;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioCancelacionesSql implements RepositorioCancelaciones {

    private final JdbcClient jdbc;

    public RepositorioCancelacionesSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Una reserva tiene como máximo una cancelación (restricción única de reserva_id). */
    @Override
    public Cancelacion guardar(Cancelacion cancelacion) {
        try {
            jdbc.sql("""
                    INSERT INTO cancelacion (id, reserva_id, usuario_id, fecha, motivo, valor_devolucion, estado_final)
                    VALUES (:id, :reserva, :usuario, :fecha, :motivo, :valor, :estadoFinal)""")
                    .param("id", cancelacion.id())
                    .param("reserva", cancelacion.reservaId())
                    .param("usuario", cancelacion.usuarioId())
                    .param("fecha", Timestamp.from(cancelacion.fecha()))
                    .param("motivo", cancelacion.motivo())
                    .param("valor", cancelacion.valorDevolucion())
                    .param("estadoFinal", cancelacion.estadoFinal().name())
                    .update();
            return cancelacion;
        } catch (DuplicateKeyException e) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "La reserva ya fue cancelada.");
        }
    }

    @Override
    public List<Cancelacion> buscarPorReservas(Collection<UUID> reservaIds) {
        return jdbc.sql("SELECT * FROM cancelacion WHERE reserva_id IN (:reservas)")
                .param("reservas", reservaIds)
                .query((fila, numero) -> new Cancelacion(
                        fila.getObject("id", UUID.class),
                        fila.getObject("reserva_id", UUID.class),
                        fila.getObject("usuario_id", UUID.class),
                        fila.getTimestamp("fecha") == null ? null : fila.getTimestamp("fecha").toInstant(),
                        fila.getString("motivo"),
                        fila.getBigDecimal("valor_devolucion"),
                        Cancelacion.EstadoFinal.valueOf(fila.getString("estado_final"))))
                .list();
    }
}
