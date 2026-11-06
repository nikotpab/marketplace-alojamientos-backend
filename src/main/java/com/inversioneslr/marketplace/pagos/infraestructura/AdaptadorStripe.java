package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import java.math.BigDecimal;

/** Único lugar con código de Stripe (ADR-08). */
class AdaptadorStripe implements PasarelaPagos {

    /** Presupuesto total de la llamada, incluidos conexión y reintentos del SDK (RNF-11). */
    private static final int TIEMPO_MAX_SEGUNDOS = 7;

    private final String claveSecreta;

    AdaptadorStripe(String claveSecreta) {
        this.claveSecreta = claveSecreta;
    }

    @Override
    public String cobrar(BigDecimal monto, String clave) {
        throw new UnsupportedOperationException("CU-19: crear el pago en Stripe con la clave de idempotencia");
    }

    @Override
    public void devolver(String referencia, BigDecimal monto) {
        throw new UnsupportedOperationException("CU-20: crear la devolución en Stripe");
    }
}
