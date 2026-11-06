package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.pagos.dominio.Pago;
import com.inversioneslr.marketplace.pagos.dominio.RepositorioPagos;
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
}
