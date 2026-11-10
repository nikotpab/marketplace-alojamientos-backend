package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.pagos.dominio.Pago;
import com.inversioneslr.marketplace.pagos.dominio.RepositorioPagos;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioPagosSql implements RepositorioPagos {

    private final JdbcClient jdbc;

    public RepositorioPagosSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(Pago pago) {
        jdbc.sql("""
                INSERT INTO pago (id, reserva_id, id_pago_stripe, clave_idempotencia, monto, estado)
                VALUES (:id, :reserva, :referencia, :clave, :monto, :estado)""")
                .param("id", pago.id())
                .param("reserva", pago.reservaId())
                .param("referencia", pago.referencia())
                .param("clave", pago.claveIdempotencia())
                .param("monto", pago.monto())
                .param("estado", pago.estado().name())
                .update();
    }

    @Override
    public Optional<String> marcarDevuelto(UUID reservaId) {
        return jdbc.sql("UPDATE pago SET estado = 'DEVUELTO' WHERE reserva_id = :reserva AND estado = 'APROBADO' RETURNING id_pago_stripe")
                .param("reserva", reservaId)
                .query(String.class)
                .optional();
    }

    @Override
    public void marcarAprobado(UUID reservaId) {
        jdbc.sql("UPDATE pago SET estado = 'APROBADO' WHERE reserva_id = :reserva AND estado = 'DEVUELTO'")
                .param("reserva", reservaId)
                .update();
    }
}
