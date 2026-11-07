package com.inversioneslr.marketplace.busqueda.dominio;

import java.util.List;

public record PaginaAlojamientos(List<AlojamientoResumen> contenido, long total, int pagina, int totalPaginas) {
}
