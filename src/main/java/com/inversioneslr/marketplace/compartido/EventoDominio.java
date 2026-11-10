package com.inversioneslr.marketplace.compartido;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hecho del dominio, por ejemplo tipo = "ReservaConfirmada" (ADR-06).
 *
 * @param id            identifica el evento, para que entregarlo otra vez no repita sus efectos (RNF-14)
 * @param actorId       quién lo provocó; null si fue el sistema
 * @param destinatarios usuarios a notificar
 */
public record EventoDominio(
        UUID id,
        String tipo,
        UUID actorId,
        String entidad,
        UUID entidadId,
        String resultado,
        Map<String, Object> detalle,
        List<UUID> destinatarios) {

    /** Un evento nuevo, con su propio id. */
    public EventoDominio(String tipo, UUID actorId, String entidad, UUID entidadId, String resultado,
                         Map<String, Object> detalle, List<UUID> destinatarios) {
        this(UUID.randomUUID(), tipo, actorId, entidad, entidadId, resultado, detalle, destinatarios);
    }
}
