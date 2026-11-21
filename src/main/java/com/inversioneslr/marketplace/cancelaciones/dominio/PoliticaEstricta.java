package com.inversioneslr.marketplace.cancelaciones.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SCRUM-114: 50 % hasta 14 días antes de la llegada y nada después.
 * La tarifa de servicio no es reembolsable. El valor se redondea al peso, como la cotización.
 */
public class PoliticaEstricta implements PoliticaCancelacion {

    private static final Tramo DEVOLUCION_PARCIAL = new Tramo(14, 50);

    @Override
    public BigDecimal calcularDevolucion(ReservaCancelable reserva, LocalDate fechaCancelacion) {
        return PoliticaCancelacion.porTramos(reserva, fechaCancelacion, DEVOLUCION_PARCIAL);
    }
}
