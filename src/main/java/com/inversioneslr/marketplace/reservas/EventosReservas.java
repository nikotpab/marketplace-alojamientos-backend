package com.inversioneslr.marketplace.reservas;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de reservas (SCRUM-143). */
public interface EventosReservas {

    String TOTAL = "total";
    String PUBLICACION = "publicacion";

    /** Sobre una reserva. */
    interface DeReserva extends EventoDominio {

        UUID reservaId();

        @Override
        default String entidad() {
            return "reserva";
        }

        @Override
        default UUID entidadId() {
            return reservaId();
        }
    }

    record ReservaConfirmada(UUID id, UUID huespedId, UUID reservaId, BigDecimal total, String codigo, UUID anfitrionId)
            implements DeReserva {

        public ReservaConfirmada(UUID huespedId, UUID reservaId, BigDecimal total, String codigo, UUID anfitrionId) {
            this(UUID.randomUUID(), huespedId, reservaId, total, codigo, anfitrionId);
        }

        @Override
        public UUID actorId() {
            return huespedId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of(TOTAL, total, "codigo", codigo);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(huespedId, anfitrionId);
        }
    }

    /** La provoca el sistema cuando pasa la fecha de salida. */
    record ReservaCompletada(UUID id, UUID reservaId, UUID huespedId) implements DeReserva {

        public ReservaCompletada(UUID reservaId, UUID huespedId) {
            this(UUID.randomUUID(), reservaId, huespedId);
        }

        @Override
        public UUID actorId() {
            return null;
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
            return PUBLICACION;
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
            return Map.of(TOTAL, total);
        }
    }

    /** CU-24: la publicación fue bloqueada con esta reserva pendiente. */
    record ReservaEnRevision(UUID id, UUID reservaId, UUID publicacionId, UUID huespedId) implements DeReserva {

        public ReservaEnRevision(UUID reservaId, UUID publicacionId, UUID huespedId) {
            this(UUID.randomUUID(), reservaId, publicacionId, huespedId);
        }

        @Override
        public UUID actorId() {
            return null;
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

    /** SEC-03: otra reserva ganó el periodo y el cobro no se pudo devolver. La reconciliación lo reintenta. */
    record DevolucionFallida(UUID id, UUID huespedId, UUID publicacionId, String causa, String referencia, BigDecimal total)
            implements EventoDominio {

        public DevolucionFallida(UUID huespedId, UUID publicacionId, String causa, String referencia, BigDecimal total) {
            this(UUID.randomUUID(), huespedId, publicacionId, causa, referencia, total);
        }

        @Override
        public UUID actorId() {
            return huespedId;
        }

        @Override
        public String entidad() {
            return PUBLICACION;
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
            return Map.of("referencia", referencia, TOTAL, total);
        }
    }
}
