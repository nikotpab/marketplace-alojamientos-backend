package com.inversioneslr.marketplace.usuarios;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de usuarios (SCRUM-143). */
public interface EventosUsuarios {

    /** Sobre una cuenta. Salvo que el record diga otra cosa, la provoca su propio usuario. */
    interface DeUsuario extends EventoDominio {

        UUID usuarioId();

        @Override
        default UUID actorId() {
            return usuarioId();
        }

        @Override
        default String entidad() {
            return "usuario";
        }

        @Override
        default UUID entidadId() {
            return usuarioId();
        }
    }

    record UsuarioRegistrado(UUID id, UUID usuarioId, String rol) implements DeUsuario {

        public UsuarioRegistrado(UUID usuarioId, String rol) {
            this(UUID.randomUUID(), usuarioId, rol);
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("rol", rol);
        }
    }

    record InicioSesionExitoso(UUID id, UUID usuarioId) implements DeUsuario {

        public InicioSesionExitoso(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }
    }

    /** RNF-20. El usuario es null si el correo no existe. */
    record InicioSesionFallido(UUID id, UUID usuarioId, String causa) implements DeUsuario {

        public InicioSesionFallido(UUID usuarioId, String causa) {
            this(UUID.randomUUID(), usuarioId, causa);
        }

        @Override
        public String resultado() {
            return causa;
        }
    }

    record RecuperacionSolicitada(UUID id, UUID usuarioId) implements DeUsuario {

        public RecuperacionSolicitada(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }
    }

    /** CU-25. */
    record UsuarioRestringido(UUID id, UUID administradorId, UUID usuarioId, String motivo) implements DeUsuario {

        public UsuarioRestringido(UUID administradorId, UUID usuarioId, String motivo) {
            this(UUID.randomUUID(), administradorId, usuarioId, motivo);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("motivo", motivo);
        }
    }

    record ContrasenaRestablecida(UUID id, UUID usuarioId) implements DeUsuario {

        public ContrasenaRestablecida(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }
    }

    record CorreoVerificado(UUID id, UUID usuarioId) implements DeUsuario {

        public CorreoVerificado(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }
    }

    record PerfilActualizado(UUID id, UUID usuarioId) implements DeUsuario {

        public PerfilActualizado(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }
    }

    record CuentaActivada(UUID id, UUID usuarioId) implements DeUsuario {

        public CuentaActivada(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }
    }

    record CuentaDesactivada(UUID id, UUID usuarioId) implements DeUsuario {

        public CuentaDesactivada(UUID usuarioId) {
            this(UUID.randomUUID(), usuarioId);
        }
    }
}
