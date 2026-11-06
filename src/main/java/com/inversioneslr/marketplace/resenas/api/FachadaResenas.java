package com.inversioneslr.marketplace.resenas.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FachadaResenas {

    /** CU-23: excluye las ocultas. */
    List<ResenaVisible> listarVisibles(UUID publicacionId);

    /** CU-26: la reseña se oculta, no se elimina. */
    void ocultar(UUID administradorId, UUID resenaId);

    record ResenaVisible(UUID id, int calificacion, String contenido, Instant fecha) {
    }
}
