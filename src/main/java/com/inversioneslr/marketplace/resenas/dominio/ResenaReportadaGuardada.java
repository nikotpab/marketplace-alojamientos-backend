package com.inversioneslr.marketplace.resenas.dominio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Una reseña visible con sus reportes, tal como se lee de la tabla. */
public record ResenaReportadaGuardada(UUID id, UUID reservaId, int calificacion, String contenido, Instant creadoEn,
                                      long reportes, List<String> motivos, Instant ultimoReporte) {
}
