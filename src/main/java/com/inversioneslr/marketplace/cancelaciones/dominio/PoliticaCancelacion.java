package com.inversioneslr.marketplace.cancelaciones.dominio;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Strategy: una política nueva es una clase nueva y una línea en {@link #porNombre}. */
public interface PoliticaCancelacion {

    BigDecimal calcularDevolucion(ReservaCancelable reserva, LocalDate fechaCancelacion);

    /**
     * Devuelve el porcentaje del primer tramo que se cumple, sobre el total sin la tarifa de servicio, que
     * no es reembolsable. Los tramos van del más lejano al más cercano a la llegada. Se redondea al peso.
     */
    static BigDecimal porTramos(ReservaCancelable reserva, LocalDate fechaCancelacion, Tramo... tramos) {
        long diasAntes = ChronoUnit.DAYS.between(fechaCancelacion, reserva.fechaLlegada());
        BigDecimal reembolsable = reserva.total().subtract(reserva.tarifaServicio());
        for (Tramo tramo : tramos) {
            if (diasAntes >= tramo.diasAntes()) {
                return reembolsable.multiply(BigDecimal.valueOf(tramo.porcentaje())).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }

    /** Desde cuántos días antes de la llegada se devuelve ese porcentaje. */
    record Tramo(int diasAntes, int porcentaje) {
    }

    /** Una política sin clase responde un error claro en vez de inventar reglas. */
    static PoliticaCancelacion porNombre(String nombre) {
        return switch (nombre) {
            case "Flexible" -> new PoliticaFlexible();
            case "Moderada" -> new PoliticaModerada();
            case "Estricta" -> new PoliticaEstricta();
            default -> throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "La política de cancelación " + nombre
                    + " todavía no tiene reglas definidas, así que no se puede calcular la devolución.");
        };
    }
}
