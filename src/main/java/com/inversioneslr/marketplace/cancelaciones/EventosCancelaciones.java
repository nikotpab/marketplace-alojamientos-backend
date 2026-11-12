package com.inversioneslr.marketplace.cancelaciones;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de cancelaciones (SCRUM-143). */
public interface EventosCancelaciones {

    record ReservaCancelada(UUID id, UUID huespedId, UUID reservaId, String estadoFinal, String motivo, BigDecimal valorDevolucion, UUID anfitrionId) implements EventoDominio {

        public ReservaCancelada(UUID huespedId, UUID reservaId, String estadoFinal, String motivo, BigDecimal valorDevolucion, UUID anfitrionId) {
            this(UUID.randomUUID(), huespedId, reservaId, estadoFinal, motivo, valorDevolucion, anfitrionId);
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
        public String resultado() {
            return estadoFinal;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("motivo", motivo, "valorDevolucion", valorDevolucion);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(huespedId, anfitrionId);
        }
    }

    record DevolucionProcesada(UUID id, UUID huespedId, UUID reservaId, BigDecimal valorDevolucion) implements EventoDominio {

        public DevolucionProcesada(UUID huespedId, UUID reservaId, BigDecimal valorDevolucion) {
            this(UUID.randomUUID(), huespedId, reservaId, valorDevolucion);
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
            return Map.of("valorDevolucion", valorDevolucion);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(huespedId);
        }
    }

    /** La reserva queda cancelada igual (pendiente 3 del README). */
    record DevolucionFallida(UUID id, UUID huespedId, UUID reservaId, BigDecimal valorDevolucion) implements EventoDominio {

        public DevolucionFallida(UUID huespedId, UUID reservaId, BigDecimal valorDevolucion) {
            this(UUID.randomUUID(), huespedId, reservaId, valorDevolucion);
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
        public String resultado() {
            return "FALLIDO";
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("valorDevolucion", valorDevolucion);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(huespedId);
        }
    }
}
