package com.inversioneslr.marketplace.resenas;

import com.inversioneslr.marketplace.compartido.PublicacionId;
import com.inversioneslr.marketplace.compartido.ResenaId;
import com.inversioneslr.marketplace.compartido.UsuarioId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FachadaResenas {

    /** CU-23: excluye las ocultas. */
    List<ResenaVisible> listarVisibles(PublicacionId publicacionId);

    /** CU-26: la reseña se oculta, no se elimina. */
    void ocultar(UsuarioId administradorId, ResenaId resenaId);

    /** SCRUM-111 · RF-29: las reseñas visibles que alguien reportó, de la más reportada a la menos. */
    List<ResenaConReportes> listarReportadas();

    /** @param autor nombre e inicial del apellido del huésped, para no exponer sus datos */
    record ResenaVisible(UUID id, String autor, int calificacion, String comentario, Instant fecha) {
    }

    /** @param motivos los distintos motivos con que la reportaron */
    record ResenaConReportes(UUID id, UUID publicacionId, String autor, int calificacion, String comentario, Instant fecha,
                             long reportes, List<String> motivos, Instant ultimoReporte) {
    }
}
