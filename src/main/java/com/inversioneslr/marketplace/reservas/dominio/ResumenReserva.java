package com.inversioneslr.marketplace.reservas.dominio;

import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Vista de lectura enriquecida con datos de publicacion y, para consultas de anfitrión, nombre del huésped. */
public record ResumenReserva(UUID id, UUID huespedId, UUID publicacionId, LocalDate fechaLlegada,
                              LocalDate fechaSalida, int numeroHuespedes, EstadoReserva estado, int noches,
                              BigDecimal precioBase, BigDecimal tarifaLimpieza, BigDecimal tarifaServicio,
                              BigDecimal descuento, BigDecimal total, String titulo, String ciudad,
                              String nombreHuesped) {
}
