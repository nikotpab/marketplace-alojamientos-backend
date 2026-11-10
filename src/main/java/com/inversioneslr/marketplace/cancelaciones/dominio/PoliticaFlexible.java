package com.inversioneslr.marketplace.cancelaciones.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 100 % hasta 5 días antes de la llegada, 50 % entre 5 y 2 días antes y nada después.
 * La tarifa de servicio no es reembolsable. El valor se redondea al peso, como la cotización.
 */
public class PoliticaFlexible implements PoliticaCancelacion {

    private static final int DIAS_DEVOLUCION_TOTAL = 5;
    private static final int DIAS_DEVOLUCION_PARCIAL = 2;

    @Override
    public BigDecimal calcularDevolucion(ReservaCancelable reserva, LocalDate fechaCancelacion) {
        BigDecimal reembolsable = reserva.total().subtract(reserva.tarifaServicio());
        long diasAntes = ChronoUnit.DAYS.between(fechaCancelacion, reserva.fechaLlegada());
        if (diasAntes >= DIAS_DEVOLUCION_TOTAL) {
            return reembolsable.setScale(0, RoundingMode.HALF_UP);
        }
        if (diasAntes >= DIAS_DEVOLUCION_PARCIAL) {
            return reembolsable.divide(BigDecimal.TWO, 0, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
}
