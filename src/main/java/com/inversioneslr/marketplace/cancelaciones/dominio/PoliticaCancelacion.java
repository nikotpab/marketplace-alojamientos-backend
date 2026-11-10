package com.inversioneslr.marketplace.cancelaciones.dominio;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Strategy (RNF-31): una política nueva es una clase nueva y una línea en {@link #porNombre}. */
public interface PoliticaCancelacion {

    BigDecimal calcularDevolucion(ReservaCancelable reserva, LocalDate fechaCancelacion);

    /** Moderada y Estricta aún no tienen reglas definidas (pendiente 6 del README): responden un error claro. */
    static PoliticaCancelacion porNombre(String nombre) {
        return switch (nombre) {
            case "Flexible" -> new PoliticaFlexible();
            default -> throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "La política de cancelación " + nombre
                    + " todavía no tiene reglas definidas, así que no se puede calcular la devolución.");
        };
    }
}
