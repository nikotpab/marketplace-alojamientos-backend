package com.inversioneslr.marketplace.resenas.dominio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioResenas {

    Resena guardar(Resena resena);

    Optional<Resena> buscarPorId(UUID id);

    /** @return vacío si no existe o está oculta */
    Optional<ResenaGuardada> buscarVisible(UUID id);

    /** Las visibles de esas reservas, de la más reciente a la más antigua. */
    List<ResenaGuardada> listarVisibles(Collection<UUID> reservaIds);

    /**
     * SCRUM-111: guarda el reporte. Un usuario reporta una reseña una sola vez.
     *
     * @throws com.inversioneslr.marketplace.compartido.ExcepcionNegocio CONFLICTO si ya la había reportado
     */
    void reportar(UUID resenaId, UUID usuarioId, String motivo, String comentario);

    /** SCRUM-111: las reseñas visibles con al menos un reporte, de la más reportada a la menos. */
    List<ResenaReportadaGuardada> listarReportadas();

    /** Una sola consulta: cuáles de esas reservas ya tienen reseña, oculta o no (SCRUM-113). */
    List<UUID> reservasConResena(Collection<UUID> reservaIds);
}
