package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.pagos.dominio.Pago;
import com.inversioneslr.marketplace.pagos.dominio.RepositorioPagos;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
    public void registrarIntento(String clave, BigDecimal monto) {
        jdbc.sql("""
                INSERT INTO pago (clave_idempotencia, monto, estado) VALUES (:clave, :monto, 'PENDIENTE')
                ON CONFLICT (clave_idempotencia) DO NOTHING""")
                .param("clave", clave)
                .param("monto", monto)
                .update();
    }

    @Override
    public void guardar(Pago pago) {
        jdbc.sql("""
                INSERT INTO pago (id, reserva_id, id_pago_stripe, clave_idempotencia, monto, estado)
                VALUES (:id, :reserva, :referencia, :clave, :monto, :estado)
                ON CONFLICT (clave_idempotencia) DO UPDATE SET
                    reserva_id = EXCLUDED.reserva_id,
                    id_pago_stripe = EXCLUDED.id_pago_stripe,
                    monto = EXCLUDED.monto,
                    estado = EXCLUDED.estado""")
                .param("id", pago.id())
                .param("reserva", pago.reservaId())
                .param("referencia", pago.referencia())
                .param("clave", pago.claveIdempotencia())
                .param("monto", pago.monto())
                .param("estado", pago.estado().name())
                .update();
    }

    @Override
    public void marcarRechazado(String clave) {
        jdbc.sql("UPDATE pago SET estado = 'RECHAZADO' WHERE clave_idempotencia = :clave AND estado = 'PENDIENTE'")
                .param("clave", clave)
                .update();
    }

    @Override
    public void marcarDevueltoSinReserva(String clave, String referencia) {
        jdbc.sql("""
                UPDATE pago SET estado = 'DEVUELTO', id_pago_stripe = :referencia
                WHERE clave_idempotencia = :clave AND estado = 'PENDIENTE'""")
                .param("referencia", referencia)
                .param("clave", clave)
                .update();
    }

    @Override
    public List<Pago> listarPendientesCreadosAntesDe(Instant momento) {
        return jdbc.sql("SELECT * FROM pago WHERE estado = 'PENDIENTE' AND creado_en < :momento ORDER BY creado_en")
                .param("momento", Timestamp.from(momento))
                .query((fila, numero) -> new Pago(
                        fila.getObject("id", UUID.class),
                        fila.getObject("reserva_id", UUID.class),
                        fila.getString("id_pago_stripe"),
                        fila.getString("clave_idempotencia"),
                        fila.getBigDecimal("monto"),
                        Pago.Estado.valueOf(fila.getString("estado"))))
                .list();
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
