package com.inversioneslr.marketplace.publicaciones.dominio;

import java.time.LocalDate;
import java.util.List;

/**
 * Días no disponibles de un rango, como los pinta el calendario del frontend. Los días que no aparecen están disponibles.
 *
 * @param bloqueados sin periodo disponible o bloqueados por el anfitrión
 * @param reservados con una reserva confirmada
 */
public record Disponibilidad(List<LocalDate> bloqueados, List<LocalDate> reservados) {
}
