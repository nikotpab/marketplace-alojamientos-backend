package com.inversioneslr.marketplace.auditoria.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FachadaAuditoria {

    /** CU-27. */
    List<EventoAuditoria> consultar(FiltrosAuditoria filtros);

    /** @param actor y rol quedan vacíos si el evento lo provocó el sistema */
    record EventoAuditoria(UUID id, UUID actorId, String actor, String rol, String accion, String entidad, UUID entidadId,
                           String resultado, Map<String, Object> detalle, String hashRegistro, Instant fecha) {
    }

    /** Todos los filtros son opcionales. Las fechas se interpretan en hora de Colombia. */
    record FiltrosAuditoria(UUID actorId, String accion, String entidad, LocalDate desde, LocalDate hasta, String resultado) {
    }
}
