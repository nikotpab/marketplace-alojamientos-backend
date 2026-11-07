package com.inversioneslr.marketplace.busqueda.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** @param calificacionPromedio promedio de las reseñas visibles; null si aún no tiene */
public record AlojamientoDetalle(UUID id, String titulo, String descripcion, String ciudad, String direccion, String tipo,
                                 int capacidad, BigDecimal precioNoche, List<String> servicios, List<String> reglas,
                                 String politicaCancelacion, List<String> imagenes, BigDecimal calificacionPromedio) {
}
