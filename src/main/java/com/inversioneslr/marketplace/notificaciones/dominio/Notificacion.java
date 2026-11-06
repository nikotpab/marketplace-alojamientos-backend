package com.inversioneslr.marketplace.notificaciones.dominio;

import java.time.Instant;
import java.util.UUID;

public record Notificacion(UUID id, UUID usuarioId, String tipo, String mensaje, boolean leida, Instant creadoEn) {

    public Notificacion marcarLeida() {
        throw new UnsupportedOperationException("RF-24");
    }
}
