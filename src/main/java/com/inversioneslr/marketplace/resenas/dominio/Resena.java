package com.inversioneslr.marketplace.resenas.dominio;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import java.util.UUID;

public record Resena(UUID id, UUID reservaId, int calificacion, String contenido, boolean oculta, UUID ocultadaPor) {

    /** RF-29: se oculta, no se borra. Una reseña oculta no se muestra ni cuenta en el promedio. */
    public Resena ocultar(UUID administradorId) {
        if (oculta) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "La reseña ya está oculta.");
        }
        return new Resena(id, reservaId, calificacion, contenido, true, administradorId);
    }
}
