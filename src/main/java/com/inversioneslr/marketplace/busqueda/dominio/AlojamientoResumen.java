package com.inversioneslr.marketplace.busqueda.dominio;

import java.math.BigDecimal;
import java.util.UUID;

public record AlojamientoResumen(UUID id, String titulo, String ciudad, String tipo, int capacidad,
                                 BigDecimal precioNoche, String imagen) {
}
