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
}
