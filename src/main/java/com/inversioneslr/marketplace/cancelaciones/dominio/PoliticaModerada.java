package com.inversioneslr.marketplace.cancelaciones.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 100 % hasta 14 días antes de la llegada, 50 % entre 13 y 7 días antes y nada después.
 * La tarifa de servicio no es reembolsable. El valor se redondea al peso, como la cotización.
 */
public class PoliticaModerada implements PoliticaCancelacion {

    private static final Tramo DEVOLUCION_TOTAL = new Tramo(14, 100);
    private static final Tramo DEVOLUCION_PARCIAL = new Tramo(7, 50);

    @Override
    public BigDecimal calcularDevolucion(ReservaCancelable reserva, LocalDate fechaCancelacion) {
        return PoliticaCancelacion.porTramos(reserva, fechaCancelacion, DEVOLUCION_TOTAL, DEVOLUCION_PARCIAL);
    }
}
