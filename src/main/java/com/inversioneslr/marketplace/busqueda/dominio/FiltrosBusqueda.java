package com.inversioneslr.marketplace.busqueda.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FiltrosBusqueda(String ciudad, LocalDate fechaLlegada, LocalDate fechaSalida, Integer capacidad,
                              UUID tipoAlojamientoId, BigDecimal precioMinimo, BigDecimal precioMaximo,
                              List<UUID> servicios) {
}
