package com.inversioneslr.marketplace.compartido;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de una reserva (SCRUM-144). Con un tipo por entidad, el compilador no deja pasar un id en el lugar de
 * otro, por ejemplo invertir los argumentos de {@code bloquear(administradorId, publicacionId, motivo)}. Solo se
 * usa entre módulos: el JSON y la base de datos siguen con el UUID.
 */
public record ReservaId(UUID valor) {

    public ReservaId {
        Objects.requireNonNull(valor, "El id no puede ser nulo.");
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
