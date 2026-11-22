package com.inversioneslr.marketplace.resenas;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de reseñas. */
public interface EventosResenas {

    /** Sobre una reseña. */
    interface DeResena extends EventoDominio {

        UUID resenaId();

        @Override
        default String entidad() {
            return "resena";
        }

        @Override
        default UUID entidadId() {
            return resenaId();
        }
    }

    record ResenaPublicada(UUID id, UUID huespedId, UUID resenaId, UUID reservaId, UUID publicacionId, int calificacion) implements DeResena {

        public ResenaPublicada(UUID huespedId, UUID resenaId, UUID reservaId, UUID publicacionId, int calificacion) {
            this(UUID.randomUUID(), huespedId, resenaId, reservaId, publicacionId, calificacion);
        }

        @Override
        public UUID actorId() {
            return huespedId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("reservaId", reservaId, "publicacionId", publicacionId, "calificacion", calificacion);
        }
    }

    record ResenaOcultada(UUID id, UUID administradorId, UUID resenaId) implements DeResena {

        public ResenaOcultada(UUID administradorId, UUID resenaId) {
            this(UUID.randomUUID(), administradorId, resenaId);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }
    }

    /** Un usuario reporta una reseña. Queda en la auditoría con el motivo. */
    record ResenaReportada(UUID id, UUID usuarioId, UUID resenaId, String motivo) implements DeResena {

        public ResenaReportada(UUID usuarioId, UUID resenaId, String motivo) {
            this(UUID.randomUUID(), usuarioId, resenaId, motivo);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("motivo", motivo);
        }
    }
}
