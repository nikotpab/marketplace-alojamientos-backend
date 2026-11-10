package com.inversioneslr.marketplace.compartido;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hecho del dominio, por ejemplo tipo = "ReservaConfirmada" (ADR-06).
 *
 * @param actorId       quién lo provocó; null si fue el sistema
 * @param destinatarios usuarios a notificar
 */
public record EventoDominio(
        String tipo,
        UUID actorId,
        String entidad,
        UUID entidadId,
        String resultado,
        Map<String, Object> detalle,
        List<UUID> destinatarios) {
}
