package com.inversioneslr.marketplace.administracion;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de administración (SCRUM-143). */
public interface EventosAdministracion {

    /** RF-34: quién exportó, qué reporte y con qué filtros. */
    record ReporteExportado(UUID id, UUID administradorId, Map<String, Object> filtros) implements EventoDominio {

        public ReporteExportado(UUID administradorId, Map<String, Object> filtros) {
            this(UUID.randomUUID(), administradorId, filtros);
        }

        @Override
        public UUID actorId() {
            return administradorId;
        }

        @Override
        public String entidad() {
            return "reporte";
        }

        @Override
        public UUID entidadId() {
            return null;
        }

        @Override
        public Map<String, Object> detalle() {
            return filtros;
        }
    }
}
