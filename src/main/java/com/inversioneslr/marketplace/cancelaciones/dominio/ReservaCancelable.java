package com.inversioneslr.marketplace.cancelaciones.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReservaCancelable(LocalDate fechaLlegada, BigDecimal total, BigDecimal tarifaServicio) {
}
