package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.inversioneslr.marketplace.pagos.FachadaPagos;
import com.inversioneslr.marketplace.pagos.aplicacion.ServicioPagos;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PagosIT extends PruebaIntegracion {

    @Autowired
    FachadaPagos pagos;

    @Autowired
    ServicioPagos servicio;

    @Test
    void elAdaptadorSimuladoApruebaYElPagoQuedaRegistrado() {
        String clave = UUID.randomUUID().toString();

        String referencia = pagos.cobrar(new BigDecimal("1062000.00"), clave);
        pagos.registrarPago(null, referencia, clave, new BigDecimal("1062000.00"));

        var pago = comoAplicacion.queryForMap("SELECT id_pago_stripe, monto, estado FROM pago WHERE clave_idempotencia = ?", clave);
        assertThat(pago).containsEntry("id_pago_stripe", referencia).containsEntry("estado", "APROBADO");
    }

    /** SCRUM-118 · ADR-04: pasadas 24 h, un reintento con la misma clave es un intento nuevo y no pisa el pago anterior. */
    @Test
    void losPagosResueltosSueltanSuClaveA24HorasYLosPendientesLaConservan() {
        String vieja = pago("APROBADO", 25);
        String reciente = pago("APROBADO", 23);
        String pendiente = pago("PENDIENTE", 25);

        servicio.liberarClavesVencidas();

        assertThat(contar(vieja)).isZero();
        assertThat(contar(reciente)).isEqualTo(1);
        assertThat(contar(pendiente)).isEqualTo(1);
        // Sin esto, la reconciliación de otras pruebas (ReconciliacionIT) intentaría revertir este cobro pendiente.
        comoAplicacion.update("UPDATE pago SET estado = 'RECHAZADO' WHERE clave_idempotencia = ?", pendiente);

        String referencia = pagos.cobrar(new BigDecimal("50000.00"), vieja);
        pagos.registrarPago(null, referencia, vieja, new BigDecimal("50000.00"));
        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM pago WHERE id_pago_stripe = ?", Long.class, "pi_" + vieja)).isEqualTo(1);
        assertThat(contar(vieja)).isEqualTo(1);
    }

    private String pago(String estado, int horasAtras) {
        String clave = UUID.randomUUID().toString();
        comoAplicacion.update("""
                INSERT INTO pago (id_pago_stripe, clave_idempotencia, monto, estado, creado_en)
                VALUES (?, ?, 50000, ?, now() - make_interval(hours => ?))""", "pi_" + clave, clave, estado, horasAtras);
        return clave;
    }

    private long contar(String clave) {
        return comoAplicacion.queryForObject("SELECT count(*) FROM pago WHERE clave_idempotencia = ?", Long.class, clave);
    }
}
