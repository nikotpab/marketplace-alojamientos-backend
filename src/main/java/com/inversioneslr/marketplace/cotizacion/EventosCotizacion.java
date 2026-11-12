package com.inversioneslr.marketplace.cotizacion;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de cotización (SCRUM-143). */
public interface EventosCotizacion {

    /** CU-28. Los cambios van como "antes → después", solo lo que cambió. */
    record TarifasActualizadas(UUID id, UUID administradorId, Map<String, Object> cambios) implements EventoDominio {

        public TarifasActualizadas(UUID administradorId, Map<String, Object> cambios) {
            this(UUID.randomUUID(), administradorId, cambios);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public String entidad() {
            return "configuracion_tarifa";
        }

        @Override
        public UUID entidadId() {
            return null;
        }

        @Override
        public Map<String, Object> detalle() {
            return cambios;
        }
    }
}
