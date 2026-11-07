package com.inversioneslr.marketplace.busqueda.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Todos los filtros son opcionales. Los nombres coinciden con los parámetros que envía el frontend. */
public record FiltrosBusqueda(String ciudad, LocalDate llegada, LocalDate salida, Integer huespedes, String tipo,
                              BigDecimal precioMin, BigDecimal precioMax, List<String> servicios) {
}
