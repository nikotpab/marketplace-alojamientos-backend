package com.inversioneslr.marketplace.busqueda.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AlojamientoDetalle(UUID id, String titulo, String descripcion, String ciudad, String ubicacionTextual,
                                 String tipo, int capacidad, BigDecimal precioNoche, List<String> servicios,
                                 List<String> reglas, List<String> imagenes, String politicaCancelacion) {
}
