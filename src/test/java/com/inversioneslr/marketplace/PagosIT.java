package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.inversioneslr.marketplace.pagos.FachadaPagos;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PagosIT extends PruebaIntegracion {

    @Autowired
    FachadaPagos pagos;

    @Test
    void elAdaptadorSimuladoApruebaYElPagoQuedaRegistrado() {
        String clave = UUID.randomUUID().toString();

        String referencia = pagos.cobrar(new BigDecimal("1062000.00"), clave);
        pagos.registrarPago(null, referencia, clave, new BigDecimal("1062000.00"));

        var pago = comoAplicacion.queryForMap("SELECT id_pago_stripe, monto, estado FROM pago WHERE clave_idempotencia = ?", clave);
        assertThat(pago).containsEntry("id_pago_stripe", referencia).containsEntry("estado", "APROBADO");
    }
}
