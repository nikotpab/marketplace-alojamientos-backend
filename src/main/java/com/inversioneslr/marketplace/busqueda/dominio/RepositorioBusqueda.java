package com.inversioneslr.marketplace.busqueda.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioBusqueda {

    List<AlojamientoResumen> buscar(FiltrosBusqueda filtros, int pagina, int tamanoPagina);

    Optional<AlojamientoDetalle> buscarDetalle(UUID publicacionId);
}
