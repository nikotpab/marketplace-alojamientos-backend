package com.inversioneslr.marketplace.cancelaciones.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** RF-23: quién canceló, cuándo, por qué, cuánto se devolvió y cómo terminó la devolución. */
public record Cancelacion(UUID id, UUID reservaId, UUID usuarioId, Instant fecha, String motivo,
                          BigDecimal valorDevolucion, EstadoFinal estadoFinal) {

    public enum EstadoFinal {
        DEVOLUCION_PROCESADA,
        /** La pasarela no la procesó: la reserva queda cancelada igual (pendiente 3 del README). */
        DEVOLUCION_NO_PROCESADA,
        /** La política no devuelve nada. */
        SIN_DEVOLUCION
    }
}
