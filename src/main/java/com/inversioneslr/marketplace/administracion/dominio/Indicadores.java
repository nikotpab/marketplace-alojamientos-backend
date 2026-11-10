package com.inversioneslr.marketplace.administracion.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * RF-33. Usuarios y publicaciones activas son el total actual; lo demás cuenta las reservas hechas en el periodo.
 *
 * @param ingresosEstimados suma de las tarifas de servicio de las reservas confirmadas y completadas
 * @param ciudadesConMayorDemanda hasta 5, de la que más reservas tiene a la que menos
 */
public record Indicadores(LocalDate desde, LocalDate hasta, long usuarios, long publicacionesActivas,
                          Map<String, Long> reservasPorEstado, BigDecimal ingresosEstimados, List<String> ciudadesConMayorDemanda) {
}
