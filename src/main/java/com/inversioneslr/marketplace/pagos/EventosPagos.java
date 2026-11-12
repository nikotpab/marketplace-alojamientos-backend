package com.inversioneslr.marketplace.pagos;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de pagos (SCRUM-143). */
public interface EventosPagos {

    /** SCRUM-59: la reconciliación devolvió un cobro que no llegó a tener reserva. */
    record CobroRevertido(UUID id, UUID pagoId, BigDecimal monto, String referencia) implements EventoDominio {

        public CobroRevertido(UUID pagoId, BigDecimal monto, String referencia) {
            this(UUID.randomUUID(), pagoId, monto, referencia);
        }

        @Override
        public UUID actorId() {
            return null;
        }

        @Override
        public String entidad() {
            return "pago";
        }

        @Override
        public UUID entidadId() {
            return pagoId;
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("monto", monto, "referencia", referencia);
        }
    }
}
