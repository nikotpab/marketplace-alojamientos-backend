package com.inversioneslr.marketplace.administracion.dominio;

import java.util.List;

/** Una tabla lista para exportar: cada fila tiene un texto por columna. */
public record Reporte(String titulo, List<String> columnas, List<List<String>> filas) {
}
