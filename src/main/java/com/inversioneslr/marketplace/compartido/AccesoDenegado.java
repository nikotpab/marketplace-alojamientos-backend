package com.inversioneslr.marketplace.compartido;

import java.util.Map;
import java.util.UUID;

/** ADR-07: una petición rechazada por falta de permisos. */
public record AccesoDenegado(UUID id, UUID actorId, String metodo, String ruta) implements EventoDominio {

    public AccesoDenegado(UUID actorId, String metodo, String ruta) {
        this(UUID.randomUUID(), actorId, metodo, ruta);
    }

    @Override
    public String entidad() {
        return "ruta";
    }

    @Override
    public UUID entidadId() {
        return null;
    }

    @Override
    public String resultado() {
        return "DENEGADO";
    }

    @Override
    public Map<String, Object> detalle() {
        return Map.of("metodo", metodo, "ruta", ruta);
    }
}
