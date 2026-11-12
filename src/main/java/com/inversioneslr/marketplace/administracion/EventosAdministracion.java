package com.inversioneslr.marketplace.administracion;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.util.Map;
import java.util.UUID;

/** Eventos que emite el módulo de administración (SCRUM-143). */
public interface EventosAdministracion {

    /**
     * RF-34: quién exportó, qué reporte y con qué filtros. Es el historial de exportaciones. Los filtros van como
     * texto, con "null" si no se filtró por ellos.
     */
    record ReporteExportado(UUID id, UUID actorId, String reporte, String formato, int filas, String desde, String hasta, String ciudad)
            implements EventoDominio {

        public ReporteExportado(UUID actorId, String reporte, String formato, int filas, String desde, String hasta, String ciudad) {
            this(UUID.randomUUID(), actorId, reporte, formato, filas, desde, hasta, ciudad);
        }

        @Override
        public Map<String, Object> detalle() {
            return Map.of("tipo", reporte, "formato", formato, "filas", filas, "desde", desde, "hasta", hasta, "ciudad", ciudad);
        }

        @Override
        public String entidad() {
            return "reporte";
        }

        @Override
        public UUID entidadId() {
            return null;
        }
    }
}
