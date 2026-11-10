package com.inversioneslr.marketplace.cancelaciones.infraestructura;

import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion;
import com.inversioneslr.marketplace.cancelaciones.dominio.RepositorioCancelaciones;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import java.sql.Timestamp;
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
}
