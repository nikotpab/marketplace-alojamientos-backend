package com.inversioneslr.marketplace.resenas.dominio;

import com.inversioneslr.marketplace.resenas.api.FachadaResenas.ResenaVisible;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioResenas {

    Resena guardar(Resena resena);

    Optional<Resena> buscarPorId(UUID id);

    List<ResenaVisible> listarVisibles(UUID publicacionId);
}
