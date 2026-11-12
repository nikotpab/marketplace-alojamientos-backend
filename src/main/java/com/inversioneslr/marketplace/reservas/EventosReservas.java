package com.inversioneslr.marketplace.reservas;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de reservas (SCRUM-143). */
public interface EventosReservas {

    record ReservaConfirmada(UUID id, UUID huespedId, UUID reservaId, BigDecimal total, String codigo, UUID anfitrionId) implements EventoDominio {

        public ReservaConfirmada(UUID huespedId, UUID reservaId, BigDecimal total, String codigo, UUID anfitrionId) {
            this(UUID.randomUUID(), huespedId, reservaId, total, codigo, anfitrionId);
        }

        @Override
        public UUID actorId() {
            return huespedId;
        }

        @Override
        public String entidad() {
            return "reserva";
        }

        @Override
        public UUID entidadId() {
            return reservaId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("total", total, "codigo", codigo);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(huespedId, anfitrionId);
        }
    }

    /** La provoca el sistema cuando pasa la fecha de salida. */
    record ReservaCompletada(UUID id, UUID reservaId, UUID huespedId) implements EventoDominio {

        public ReservaCompletada(UUID reservaId, UUID huespedId) {
            this(UUID.randomUUID(), reservaId, huespedId);
        }

        @Override
        public UUID actorId() {
            return null;
        }

        @Override
        public String entidad() {
            return "reserva";
        }

        @Override
        public UUID entidadId() {
            return reservaId;
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(huespedId);
        }
    }

    /** CU-24: la publicación fue bloqueada con esta reserva pendiente. */
    record ReservaEnRevision(UUID id, UUID reservaId, UUID publicacionId, UUID huespedId) implements EventoDominio {

        public ReservaEnRevision(UUID reservaId, UUID publicacionId, UUID huespedId) {
            this(UUID.randomUUID(), reservaId, publicacionId, huespedId);
        }

        @Override
        public UUID actorId() {
            return null;
        }

        @Override
        public String entidad() {
            return "reserva";
        }

        @Override
        public UUID entidadId() {
            return reservaId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("publicacionId", publicacionId);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(huespedId);
        }
    }

    /** SEC-02: el cobro no se aprobó y no se creó la reserva. */
    record PagoFallido(UUID id, UUID huespedId, UUID publicacionId, String causa, BigDecimal total) implements EventoDominio {

        public PagoFallido(UUID huespedId, UUID publicacionId, String causa, BigDecimal total) {
            this(UUID.randomUUID(), huespedId, publicacionId, causa, total);
        }

        @Override
        public UUID actorId() {
            return huespedId;
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
        public String resultado() {
            return causa;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("total", total);
        }
    }

    /** SEC-03: otra reserva ganó el periodo y el cobro no se pudo devolver. La reconciliación lo reintenta. */
    record DevolucionFallida(UUID id, UUID huespedId, UUID publicacionId, String causa, String referencia, BigDecimal total) implements EventoDominio {

        public DevolucionFallida(UUID huespedId, UUID publicacionId, String causa, String referencia, BigDecimal total) {
            this(UUID.randomUUID(), huespedId, publicacionId, causa, referencia, total);
        }

        @Override
        public UUID actorId() {
            return huespedId;
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
        public String resultado() {
            return causa;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("referencia", referencia, "total", total);
        }
    }
}
