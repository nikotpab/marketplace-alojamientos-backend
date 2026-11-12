package com.inversioneslr.marketplace.publicaciones;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de publicaciones (SCRUM-143). */
public interface EventosPublicaciones {

    String MOTIVO = "motivo";

    /** Sobre una publicación. */
    interface DePublicacion extends EventoDominio {

        UUID publicacionId();

        @Override
        default String entidad() {
            return "publicacion";
        }

        @Override
        default UUID entidadId() {
            return publicacionId();
        }
    }

    record PublicacionCreada(UUID id, UUID anfitrionId, UUID publicacionId) implements DePublicacion {

        public PublicacionCreada(UUID anfitrionId, UUID publicacionId) {
            this(UUID.randomUUID(), anfitrionId, publicacionId);
        }

        @Override
        public UUID actorId() {
            return anfitrionId;
        }
    }

    /** La edita su anfitrión o un administrador. */
    record PublicacionEditada(UUID id, UUID usuarioId, UUID publicacionId) implements DePublicacion {

        public PublicacionEditada(UUID usuarioId, UUID publicacionId) {
            this(UUID.randomUUID(), usuarioId, publicacionId);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }
    }

    record PublicacionAprobada(UUID id, UUID administradorId, UUID publicacionId, UUID anfitrionId) implements DePublicacion {

        public PublicacionAprobada(UUID administradorId, UUID publicacionId, UUID anfitrionId) {
            this(UUID.randomUUID(), administradorId, publicacionId, anfitrionId);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(anfitrionId);
        }
    }

    record PublicacionEnviadaARevision(UUID id, UUID anfitrionId, UUID publicacionId) implements DePublicacion {

        public PublicacionEnviadaARevision(UUID anfitrionId, UUID publicacionId) {
            this(UUID.randomUUID(), anfitrionId, publicacionId);
        }

        @Override
        public UUID actorId() {
            return anfitrionId;
        }
    }

    record PublicacionRechazada(UUID id, UUID administradorId, UUID publicacionId, String motivo, UUID anfitrionId)
            implements DePublicacion {

        public PublicacionRechazada(UUID administradorId, UUID publicacionId, String motivo, UUID anfitrionId) {
            this(UUID.randomUUID(), administradorId, publicacionId, motivo, anfitrionId);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of(MOTIVO, motivo);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(anfitrionId);
        }
    }

    record PublicacionPausada(UUID id, UUID anfitrionId, UUID publicacionId) implements DePublicacion {

        public PublicacionPausada(UUID anfitrionId, UUID publicacionId) {
            this(UUID.randomUUID(), anfitrionId, publicacionId);
        }

        @Override
        public UUID actorId() {
            return anfitrionId;
        }
    }

    /** CU-12. Las fechas van como texto, igual que en la auditoría. */
    record DisponibilidadModificada(UUID id, UUID anfitrionId, UUID publicacionId, String desde, String hasta, String estado)
            implements DePublicacion {

        public DisponibilidadModificada(UUID anfitrionId, UUID publicacionId, String desde, String hasta, String estado) {
            this(UUID.randomUUID(), anfitrionId, publicacionId, desde, hasta, estado);
        }

        @Override
        public UUID actorId() {
            return anfitrionId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("desde", desde, "hasta", hasta, "estado", estado);
        }
    }

    record PublicacionReactivada(UUID id, UUID anfitrionId, UUID publicacionId) implements DePublicacion {

        public PublicacionReactivada(UUID anfitrionId, UUID publicacionId) {
            this(UUID.randomUUID(), anfitrionId, publicacionId);
        }

        @Override
        public UUID actorId() {
            return anfitrionId;
        }
    }

    /** CU-31. En la auditoría queda como TipoAlojamientoAgregado o TipoAlojamientoEditado. */
    record TipoAlojamientoGuardado(UUID id, UUID administradorId, UUID tipoId, String nombre, boolean activo, boolean nuevo)
            implements EventoDominio {

        public TipoAlojamientoGuardado(UUID administradorId, UUID tipoId, String nombre, boolean activo, boolean nuevo) {
            this(UUID.randomUUID(), administradorId, tipoId, nombre, activo, nuevo);
        }

        @Override
        public String tipo() {
            return nuevo ? "TipoAlojamientoAgregado" : "TipoAlojamientoEditado";
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

    /** CU-24. */
    record PublicacionBloqueada(UUID id, UUID administradorId, UUID publicacionId, String motivo, UUID anfitrionId)
            implements DePublicacion {

        public PublicacionBloqueada(UUID administradorId, UUID publicacionId, String motivo, UUID anfitrionId) {
            this(UUID.randomUUID(), administradorId, publicacionId, motivo, anfitrionId);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(anfitrionId);
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of(MOTIVO, motivo);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }
    }
}
