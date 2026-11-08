package com.inversioneslr.marketplace.cotizacion.api;

import java.math.BigDecimal;

/** Lo que la reserva guarda de la cotización. */
public record Cotizacion(int noches, BigDecimal precioBase, BigDecimal tarifaLimpieza, BigDecimal tarifaServicio,
                         BigDecimal descuento, BigDecimal costoAdicionales, BigDecimal total) {
}
