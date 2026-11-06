package com.inversioneslr.marketplace.auditoria.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FachadaAuditoria {

    /** CU-27. */
    List<EventoAuditoria> consultar(FiltrosAuditoria filtros);

    record EventoAuditoria(UUID id, UUID actorId, String accion, String entidad, UUID entidadId, String resultado,
                           Map<String, Object> detalle, String hashRegistro, Instant fecha) {
    }

    record FiltrosAuditoria(UUID actorId, String entidad, LocalDate desde, LocalDate hasta, String resultado) {
    }
}
