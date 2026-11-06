package com.inversioneslr.marketplace.resenas.dominio;

import java.util.UUID;

public record Resena(UUID id, UUID reservaId, int calificacion, String contenido, boolean oculta, UUID ocultadaPor) {

    public Resena ocultar(UUID administradorId) {
        throw new UnsupportedOperationException("RF-29");
    }
}
