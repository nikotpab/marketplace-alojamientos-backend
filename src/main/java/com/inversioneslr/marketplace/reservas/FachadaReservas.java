package com.inversioneslr.marketplace.reservas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface FachadaReservas {

    DatosReserva datosReserva(UUID reservaId);

    /** Al salir de CONFIRMADA, la restricción anti-solape deja de aplicar y el periodo queda libre (RF-13). */
    void marcarCancelada(UUID reservaId);

    /** CU-24: las reservas futuras se conservan y pasan a revisión manual. */
    void marcarFuturasEnRevision(UUID publicacionId);

    record DatosReserva(UUID id, UUID huespedId, UUID publicacionId, LocalDate fechaLlegada, EstadoReserva estado,
                        BigDecimal total, BigDecimal tarifaServicio) {
    }
}
