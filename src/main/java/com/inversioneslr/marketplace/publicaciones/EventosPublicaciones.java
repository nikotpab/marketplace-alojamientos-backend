package com.inversioneslr.marketplace.publicaciones;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de publicaciones (SCRUM-143). */
public interface EventosPublicaciones {

    record PublicacionCreada(UUID id, UUID anfitrionId, UUID publicacionId) implements EventoDominio {

        public PublicacionCreada(UUID anfitrionId, UUID publicacionId) {
            this(UUID.randomUUID(), anfitrionId, publicacionId);
        }

        @Override
        public UUID actorId() {
            return anfitrionId;
        }

        @Override
        public String entidad() {
            return "publicacion";
        }

        @Override
        public UUID entidadId() {
            return publicacionId;
        }
    }

    /** La edita su anfitrión o un administrador. */
    record PublicacionEditada(UUID id, UUID usuarioId, UUID publicacionId) implements EventoDominio {

        public PublicacionEditada(UUID usuarioId, UUID publicacionId) {
            this(UUID.randomUUID(), usuarioId, publicacionId);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }

        @Override
        public String entidad() {
            return "publicacion";
        }

        @Override
        public UUID entidadId() {
            return publicacionId;
        }
    }

    record PublicacionEnviadaARevision(UUID id, UUID anfitrionId, UUID publicacionId) implements EventoDominio {

        public PublicacionEnviadaARevision(UUID anfitrionId, UUID publicacionId) {
            this(UUID.randomUUID(), anfitrionId, publicacionId);
        }

        @Override
        public UUID actorId() {
            return anfitrionId;
        }

        @Override
        public String entidad() {
            return "publicacion";
        }

        @Override
        public UUID entidadId() {
            return publicacionId;
        }
    }

    record PublicacionAprobada(UUID id, UUID administradorId, UUID publicacionId, UUID anfitrionId) implements EventoDominio {

        public PublicacionAprobada(UUID administradorId, UUID publicacionId, UUID anfitrionId) {
            this(UUID.randomUUID(), administradorId, publicacionId, anfitrionId);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public String entidad() {
            return "publicacion";
        }

        @Override
        public UUID entidadId() {
            return publicacionId;
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(anfitrionId);
        }
    }

    record PublicacionRechazada(UUID id, UUID administradorId, UUID publicacionId, String motivo, UUID anfitrionId) implements EventoDominio {

        public PublicacionRechazada(UUID administradorId, UUID publicacionId, String motivo, UUID anfitrionId) {
            this(UUID.randomUUID(), administradorId, publicacionId, motivo, anfitrionId);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public String entidad() {
            return "publicacion";
        }

        @Override
        public UUID entidadId() {
            return publicacionId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("motivo", motivo);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(anfitrionId);
        }
    }

    record PublicacionPausada(UUID id, UUID anfitrionId, UUID publicacionId) implements EventoDominio {

        public PublicacionPausada(UUID anfitrionId, UUID publicacionId) {
            this(UUID.randomUUID(), anfitrionId, publicacionId);
        }

        @Override
        public UUID actorId() {
            return anfitrionId;
        }

        @Override
        public String entidad() {
            return "publicacion";
        }

        @Override
        public UUID entidadId() {
            return publicacionId;
        }
    }

    record PublicacionReactivada(UUID id, UUID anfitrionId, UUID publicacionId) implements EventoDominio {

        public PublicacionReactivada(UUID anfitrionId, UUID publicacionId) {
            this(UUID.randomUUID(), anfitrionId, publicacionId);
        }

        @Override
        public UUID actorId() {
            return anfitrionId;
        }

        @Override
        public String entidad() {
            return "publicacion";
        }

        @Override
        public UUID entidadId() {
            return publicacionId;
        }
    }

    /** CU-12. Las fechas van como texto, igual que en la auditoría. */
    record DisponibilidadModificada(UUID id, UUID anfitrionId, UUID publicacionId, String desde, String hasta, String estado) implements EventoDominio {

        public DisponibilidadModificada(UUID anfitrionId, UUID publicacionId, String desde, String hasta, String estado) {
            this(UUID.randomUUID(), anfitrionId, publicacionId, desde, hasta, estado);
        }

        @Override
        public UUID actorId() {
            return anfitrionId;
        }

        @Override
        public String entidad() {
            return "publicacion";
        }

        @Override
        public UUID entidadId() {
            return publicacionId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("desde", desde, "hasta", hasta, "estado", estado);
        }
    }

    /** CU-24. */
    record PublicacionBloqueada(UUID id, UUID administradorId, UUID publicacionId, String motivo, UUID anfitrionId) implements EventoDominio {

        public PublicacionBloqueada(UUID administradorId, UUID publicacionId, String motivo, UUID anfitrionId) {
            this(UUID.randomUUID(), administradorId, publicacionId, motivo, anfitrionId);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public String entidad() {
            return "publicacion";
        }

        @Override
        public UUID entidadId() {
            return publicacionId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("motivo", motivo);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(anfitrionId);
        }
    }

    record TipoAlojamientoAgregado(UUID id, UUID administradorId, UUID tipoId, String nombre, boolean activo) implements EventoDominio {

        public TipoAlojamientoAgregado(UUID administradorId, UUID tipoId, String nombre, boolean activo) {
            this(UUID.randomUUID(), administradorId, tipoId, nombre, activo);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public String entidad() {
            return "tipo_alojamiento";
        }

        @Override
        public UUID entidadId() {
            return tipoId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("nombre", nombre, "activo", activo);
        }
    }

    record TipoAlojamientoEditado(UUID id, UUID administradorId, UUID tipoId, String nombre, boolean activo) implements EventoDominio {

        public TipoAlojamientoEditado(UUID administradorId, UUID tipoId, String nombre, boolean activo) {
            this(UUID.randomUUID(), administradorId, tipoId, nombre, activo);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public String entidad() {
            return "tipo_alojamiento";
        }

        @Override
        public UUID entidadId() {
            return tipoId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("nombre", nombre, "activo", activo);
        }
    }
}
