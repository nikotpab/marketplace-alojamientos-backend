package com.inversioneslr.marketplace.cancelaciones;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de cancelaciones (SCRUM-143). */
public interface EventosCancelaciones {

    String VALOR_DEVOLUCION = "valorDevolucion";

    /** Sobre una reserva, por decisión de su huésped. */
    interface DeCancelacion extends EventoDominio {

        UUID reservaId();

        UUID huespedId();

        @Override
        default UUID actorId() {
            return huespedId();
        }

        @Override
        default String entidad() {
            return "reserva";
        }

        @Override
        default UUID entidadId() {
            return reservaId();
        }
    }

    record ReservaCancelada(UUID id, UUID huespedId, UUID reservaId, String estadoFinal, String motivo, BigDecimal valorDevolucion, UUID anfitrionId)
            implements DeCancelacion {

        public ReservaCancelada(UUID huespedId, UUID reservaId, String estadoFinal, String motivo, BigDecimal valorDevolucion, UUID anfitrionId) {
            this(UUID.randomUUID(), huespedId, reservaId, estadoFinal, motivo, valorDevolucion, anfitrionId);
        }

        @Override
        public String resultado() {
            return estadoFinal;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("motivo", motivo, VALOR_DEVOLUCION, valorDevolucion);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(huespedId, anfitrionId);
        }
    }

    record DevolucionProcesada(UUID id, UUID huespedId, UUID reservaId, BigDecimal valorDevolucion) implements DeCancelacion {

        public DevolucionProcesada(UUID huespedId, UUID reservaId, BigDecimal valorDevolucion) {
            this(UUID.randomUUID(), huespedId, reservaId, valorDevolucion);
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of(VALOR_DEVOLUCION, valorDevolucion);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(huespedId);
        }
    }

    /** La reserva queda cancelada igual (pendiente 3 del README). */
    record DevolucionFallida(UUID id, UUID huespedId, UUID reservaId, BigDecimal valorDevolucion) implements DeCancelacion {

        public DevolucionFallida(UUID huespedId, UUID reservaId, BigDecimal valorDevolucion) {
            this(UUID.randomUUID(), huespedId, reservaId, valorDevolucion);
        }

        @Override
        public String resultado() {
            return "FALLIDO";
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of(VALOR_DEVOLUCION, valorDevolucion);
        }

        @Override
        public List<UUID> destinatarios() {
            return List.of(huespedId);
        }
    }
}
