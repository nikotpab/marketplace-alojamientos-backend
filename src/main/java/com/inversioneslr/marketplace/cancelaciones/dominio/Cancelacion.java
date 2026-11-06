package com.inversioneslr.marketplace.cancelaciones.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Cancelacion(UUID id, UUID reservaId, UUID usuarioId, Instant fecha, String motivo,
                          BigDecimal valorDevolucion, String estadoFinal) {
}
