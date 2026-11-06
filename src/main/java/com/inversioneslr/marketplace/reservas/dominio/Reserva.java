package com.inversioneslr.marketplace.reservas.dominio;

import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Conserva el desglose cobrado: los cambios de tarifas no afectan reservas ya confirmadas. */
public record Reserva(UUID id, UUID huespedId, UUID publicacionId, LocalDate fechaLlegada, LocalDate fechaSalida,
                      int numeroHuespedes, EstadoReserva estado, int noches, BigDecimal precioBase,
                      BigDecimal tarifaLimpieza, BigDecimal tarifaServicio, BigDecimal descuento, BigDecimal total) {

    /** Solo se cancela una reserva confirmada. Al salir de CONFIRMADA, la restricción anti-solape libera el periodo. */
    public Reserva cancelar() {
        if (estado != EstadoReserva.CONFIRMADA) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "La reserva está en estado " + estado + " y no se puede cancelar.");
        }
        return new Reserva(id, huespedId, publicacionId, fechaLlegada, fechaSalida, numeroHuespedes, EstadoReserva.CANCELADA,
                noches, precioBase, tarifaLimpieza, tarifaServicio, descuento, total);
    }

    public Reserva completar() {
        throw new UnsupportedOperationException("RF-25");
    }
}
