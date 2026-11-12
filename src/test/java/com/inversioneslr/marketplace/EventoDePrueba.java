package com.inversioneslr.marketplace;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Un evento con valores a la medida de la prueba (SCRUM-143). En producción cada evento es un record de su módulo. */
record EventoDePrueba(UUID id, String tipo, UUID actorId, String entidad, UUID entidadId, String resultado,
                      Map<String, Object> detalle, List<UUID> destinatarios) implements EventoDominio {

    EventoDePrueba(String tipo, UUID actorId, String entidad, UUID entidadId, String resultado, Map<String, Object> detalle,
                   List<UUID> destinatarios) {
        this(UUID.randomUUID(), tipo, actorId, entidad, entidadId, resultado, detalle, destinatarios);
    }
}
