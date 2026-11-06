package com.inversioneslr.marketplace.cancelaciones.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 100 % hasta 5 días antes de la llegada, 50 % entre 5 y 2 días antes y nada después.
 * La tarifa de servicio no es reembolsable.
 */
public class PoliticaFlexible implements PoliticaCancelacion {

    @Override
    public BigDecimal calcularDevolucion(ReservaCancelable reserva, LocalDate fechaCancelacion) {
        throw new UnsupportedOperationException("RF-23");
    }
}
