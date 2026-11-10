package com.inversioneslr.marketplace.resenas;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FachadaResenas {

    /** CU-23: excluye las ocultas. */
    List<ResenaVisible> listarVisibles(UUID publicacionId);

    /** CU-26: la reseña se oculta, no se elimina. */
    void ocultar(UUID administradorId, UUID resenaId);

    /** @param autor nombre e inicial del apellido del huésped, para no exponer sus datos */
    record ResenaVisible(UUID id, String autor, int calificacion, String comentario, Instant fecha) {
    }
}
