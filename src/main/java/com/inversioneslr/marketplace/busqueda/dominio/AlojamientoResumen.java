package com.inversioneslr.marketplace.busqueda.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** @param calificacion promedio de las reseñas visibles; null si aún no tiene */
public record AlojamientoResumen(UUID id, String titulo, String ciudad, String tipo, int capacidad, BigDecimal precioNoche,
                                 BigDecimal calificacion, String imagen, List<String> servicios) {
}
