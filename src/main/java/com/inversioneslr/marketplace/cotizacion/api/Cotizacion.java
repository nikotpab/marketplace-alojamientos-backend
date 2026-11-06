package com.inversioneslr.marketplace.cotizacion.api;

import java.math.BigDecimal;

public record Cotizacion(int noches, BigDecimal precioBase, BigDecimal tarifaLimpieza, BigDecimal tarifaServicio,
                         BigDecimal descuento, BigDecimal costoAdicionales, BigDecimal total) {

    public BigDecimal calcularTotal() {
        throw new UnsupportedOperationException("RF-17");
    }
}
