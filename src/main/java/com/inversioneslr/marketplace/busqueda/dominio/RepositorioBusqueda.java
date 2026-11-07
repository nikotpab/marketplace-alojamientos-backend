package com.inversioneslr.marketplace.busqueda.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioBusqueda {

    PaginaAlojamientos buscar(FiltrosBusqueda filtros, int pagina, int tamanoPagina);

    /** Solo publicaciones ACTIVAS. */
    Optional<AlojamientoDetalle> buscarDetalle(UUID publicacionId);

    /** Días sin reservar: fuera de un periodo disponible, bloqueados o con una reserva confirmada. */
    List<LocalDate> fechasNoDisponibles(UUID publicacionId, LocalDate desde, LocalDate hasta);
}
