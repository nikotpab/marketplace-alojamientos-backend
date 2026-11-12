package com.inversioneslr.marketplace.usuarios;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de usuarios (SCRUM-143). */
public interface EventosUsuarios {

    record UsuarioRegistrado(UUID id, UUID usuarioId, String rol) implements EventoDominio {

        public UsuarioRegistrado(UUID usuarioId, String rol) {
            this(UUID.randomUUID(), usuarioId, rol);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }

        @Override
        public String entidad() {
            return "usuario";
        }

        @Override
        public UUID entidadId() {
            return usuarioId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("rol", rol);
        }
    }

    record InicioSesionExitoso(UUID id, UUID usuarioId) implements EventoDominio {

        public InicioSesionExitoso(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }

        @Override
        public String entidad() {
            return "usuario";
        }

        @Override
        public UUID entidadId() {
            return usuarioId;
        }
    }

    /** RNF-20. El usuario es null si el correo no existe. */
    record InicioSesionFallido(UUID id, UUID usuarioId, String causa) implements EventoDominio {

        public InicioSesionFallido(UUID usuarioId, String causa) {
            this(UUID.randomUUID(), usuarioId, causa);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }

        @Override
        public String entidad() {
            return "usuario";
        }

        @Override
        public UUID entidadId() {
            return usuarioId;
        }

        @Override
        public String resultado() {
            return causa;
        }
    }

    record RecuperacionSolicitada(UUID id, UUID usuarioId) implements EventoDominio {

        public RecuperacionSolicitada(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }

        @Override
        public String entidad() {
            return "usuario";
        }

        @Override
        public UUID entidadId() {
            return usuarioId;
        }
    }

    record ContrasenaRestablecida(UUID id, UUID usuarioId) implements EventoDominio {

        public ContrasenaRestablecida(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }

        @Override
        public String entidad() {
            return "usuario";
        }

        @Override
        public UUID entidadId() {
            return usuarioId;
        }
    }

    record CorreoVerificado(UUID id, UUID usuarioId) implements EventoDominio {

        public CorreoVerificado(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }

        @Override
        public String entidad() {
            return "usuario";
        }

        @Override
        public UUID entidadId() {
            return usuarioId;
        }
    }

    record PerfilActualizado(UUID id, UUID usuarioId) implements EventoDominio {

        public PerfilActualizado(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }

        @Override
        public String entidad() {
            return "usuario";
        }

        @Override
        public UUID entidadId() {
            return usuarioId;
        }
    }

    record CuentaActivada(UUID id, UUID usuarioId) implements EventoDominio {

        public CuentaActivada(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }

        @Override
        public String entidad() {
            return "usuario";
        }

        @Override
        public UUID entidadId() {
            return usuarioId;
        }
    }

    record CuentaDesactivada(UUID id, UUID usuarioId) implements EventoDominio {

        public CuentaDesactivada(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }

        @Override
        public UUID actorId() {
            return usuarioId;
        }

        @Override
        public String entidad() {
            return "usuario";
        }

        @Override
        public UUID entidadId() {
            return usuarioId;
        }
    }

    /** CU-25. */
    record UsuarioRestringido(UUID id, UUID administradorId, UUID usuarioId, String motivo) implements EventoDominio {

        public UsuarioRestringido(UUID administradorId, UUID usuarioId, String motivo) {
            this(UUID.randomUUID(), administradorId, usuarioId, motivo);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public String entidad() {
            return "usuario";
        }

        @Override
        public UUID entidadId() {
            return usuarioId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("motivo", motivo);
        }
    }
}
