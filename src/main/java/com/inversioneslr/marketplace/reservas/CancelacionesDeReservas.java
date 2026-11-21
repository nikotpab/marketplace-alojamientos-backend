package com.inversioneslr.marketplace.reservas;

import com.inversioneslr.marketplace.compartido.ReservaId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * SCRUM-113: lo que el historial muestra de una reserva cancelada, sin leer la tabla de cancelaciones. Lo implementa el
 * módulo de cancelaciones, que ya depende de reservas: así no hay ciclo entre los dos (como OcupacionReservas, SCRUM-142).
 */
public interface CancelacionesDeReservas {

    /** Una sola consulta para todas; las reservas sin cancelación no aparecen. */
    Map<ReservaId, CancelacionResumen> deReservas(Collection<ReservaId> reservaIds);

    /** @param estadoFinal DEVOLUCION_PROCESADA, DEVOLUCION_NO_PROCESADA o SIN_DEVOLUCION */
    record CancelacionResumen(Instant fecha, String motivo, BigDecimal valorDevolucion, String estadoFinal) {
    }
}
