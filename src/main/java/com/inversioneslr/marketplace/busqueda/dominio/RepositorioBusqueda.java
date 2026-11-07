package com.inversioneslr.marketplace.busqueda.dominio;

import java.util.Optional;
import java.util.UUID;

public interface RepositorioBusqueda {

    PaginaAlojamientos buscar(FiltrosBusqueda filtros, int pagina, int tamanoPagina);

    Optional<AlojamientoDetalle> buscarDetalle(UUID publicacionId);
}
