package com.inversioneslr.marketplace.resenas.dominio;

import java.time.Instant;
import java.util.UUID;

/** Una reseña visible, sin el autor: su nombre se pide a usuarios y su huésped a reservas (SCRUM-142). */
public record ResenaGuardada(UUID id, UUID reservaId, int calificacion, String contenido, Instant creadoEn) {
}
