package com.inversioneslr.marketplace.reservas.dominio;

import java.time.Instant;
import java.util.UUID;

/** Clave de idempotencia con el resultado original. */
public record SolicitudProcesada(String clave, UUID huespedId, UUID reservaId, String resultado, Instant expiraEn) {
}
