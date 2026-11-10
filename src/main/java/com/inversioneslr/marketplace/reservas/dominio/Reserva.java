package com.inversioneslr.marketplace.reservas.dominio;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Conserva el desglose cobrado: los cambios de tarifas no afectan reservas ya confirmadas. */
/** @param codigo lo asigna la base de datos al guardarla; null antes */
public record Reserva(UUID id, String codigo, UUID huespedId, UUID publicacionId, LocalDate fechaLlegada, LocalDate fechaSalida,
                      int numeroHuespedes, EstadoReserva estado, int noches, BigDecimal precioBase,
                      BigDecimal tarifaLimpieza, BigDecimal tarifaServicio, BigDecimal descuento, BigDecimal total) {

    /** Solo se cancela una reserva confirmada. Al salir de CONFIRMADA, la restricción anti-solape libera el periodo. */
    public Reserva cancelar() {
        if (estado != EstadoReserva.CONFIRMADA) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "La reserva está en estado " + estado + " y no se puede cancelar.");
        }
        return new Reserva(id, codigo, huespedId, publicacionId, fechaLlegada, fechaSalida, numeroHuespedes, EstadoReserva.CANCELADA,
                noches, precioBase, tarifaLimpieza, tarifaServicio, descuento, total);
    }

    public Reserva conCodigo(String asignado) {
        return new Reserva(id, asignado, huespedId, publicacionId, fechaLlegada, fechaSalida, numeroHuespedes, estado,
                noches, precioBase, tarifaLimpieza, tarifaServicio, descuento, total);
    }

    /** RF-25: solo una reserva confirmada termina como completada. */
    public Reserva completar() {
        if (estado != EstadoReserva.CONFIRMADA) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "La reserva está en estado " + estado + " y no se puede completar.");
        }
        return new Reserva(id, codigo, huespedId, publicacionId, fechaLlegada, fechaSalida, numeroHuespedes, EstadoReserva.COMPLETADA,
                noches, precioBase, tarifaLimpieza, tarifaServicio, descuento, total);
    }
}
