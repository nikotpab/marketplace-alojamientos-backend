package com.inversioneslr.marketplace.pagos.dominio;

import java.math.BigDecimal;

/** Adapter: lo único que el sistema necesita del proveedor de pagos. */
public interface PasarelaPagos {

    /**
     * @param clave      clave de idempotencia; el proveedor no cobra dos veces con la misma
     * @param metodoPago método de pago de prueba de Stripe; null usa el predeterminado
     * @return referencia del pago
     */
    String cobrar(BigDecimal monto, String clave, String metodoPago);

    void devolver(String referencia, BigDecimal monto);
}
