package com.inversioneslr.marketplace.cotizacion.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ConfiguracionTarifas(
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal porcentajeLimpieza,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal porcentajeServicio,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal porcentajeDescuento,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal porcentajeDescuentoEstanciaLarga,
        @NotNull @Positive Integer nochesEstanciaLarga) {
}
