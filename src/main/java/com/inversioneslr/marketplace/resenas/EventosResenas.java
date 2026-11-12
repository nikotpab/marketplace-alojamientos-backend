package com.inversioneslr.marketplace.resenas;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de reseñas (SCRUM-143). */
public interface EventosResenas {

    record ResenaPublicada(UUID id, UUID huespedId, UUID resenaId, UUID reservaId, UUID publicacionId, int calificacion) implements EventoDominio {

        public ResenaPublicada(UUID huespedId, UUID resenaId, UUID reservaId, UUID publicacionId, int calificacion) {
            this(UUID.randomUUID(), huespedId, resenaId, reservaId, publicacionId, calificacion);
        }

        @Override
        public UUID actorId() {
            return huespedId;
        }

        @Override
        public String entidad() {
            return "resena";
        }

        @Override
        public UUID entidadId() {
            return resenaId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("reservaId", reservaId, "publicacionId", publicacionId, "calificacion", calificacion);
        }
    }

    record ResenaOcultada(UUID id, UUID administradorId, UUID resenaId) implements EventoDominio {

        public ResenaOcultada(UUID administradorId, UUID resenaId) {
            this(UUID.randomUUID(), administradorId, resenaId);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public String entidad() {
            return "resena";
        }

        @Override
        public UUID entidadId() {
            return resenaId;
        }
    }
}
