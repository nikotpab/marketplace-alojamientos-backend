package com.inversioneslr.marketplace.cancelaciones.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Strategy (RNF-31): una política nueva es una clase nueva y una línea en {@link #porNombre}. */
public interface PoliticaCancelacion {

    BigDecimal calcularDevolucion(ReservaCancelable reserva, LocalDate fechaCancelacion);

    static PoliticaCancelacion porNombre(String nombre) {
        return switch (nombre) {
            case "Flexible" -> new PoliticaFlexible();
            default -> throw new IllegalArgumentException("Política de cancelación desconocida: " + nombre);
        };
    }
}
