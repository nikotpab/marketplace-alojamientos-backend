package com.inversioneslr.marketplace.reservas.dominio;

import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Conserva el desglose cobrado: los cambios de tarifas no afectan reservas ya confirmadas. */
public record Reserva(UUID id, UUID huespedId, UUID publicacionId, LocalDate fechaLlegada, LocalDate fechaSalida,
                      int numeroHuespedes, EstadoReserva estado, int noches, BigDecimal precioBase,
                      BigDecimal tarifaLimpieza, BigDecimal tarifaServicio, BigDecimal descuento, BigDecimal total) {

    public Reserva cancelar() {
        throw new UnsupportedOperationException("RF-22");
    }

    public Reserva completar() {
        throw new UnsupportedOperationException("RF-25");
    }
}
