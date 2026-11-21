package com.inversioneslr.marketplace.pagos.dominio;

import java.math.BigDecimal;

/** Adapter (ADR-08): lo único que el sistema necesita del proveedor de pagos. */
public interface PasarelaPagos {

    /**
     * @param clave      clave de idempotencia; el proveedor no cobra dos veces con la misma (ADR-04)
     * @param metodoPago método de pago de prueba de Stripe (CU-17); null usa el predeterminado
     * @return referencia del pago
     */
    String cobrar(BigDecimal monto, String clave, String metodoPago);

    void devolver(String referencia, BigDecimal monto);
}
