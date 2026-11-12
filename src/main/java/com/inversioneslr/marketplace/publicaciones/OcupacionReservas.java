package com.inversioneslr.marketplace.publicaciones;

import com.inversioneslr.marketplace.compartido.PublicacionId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * SCRUM-142: lo que publicaciones necesita saber de las reservas, sin leer su tabla. Lo implementa el módulo de
 * reservas, que ya depende de publicaciones: así no hay ciclo entre los dos módulos.
 */
public interface OcupacionReservas {

    /** Las reservas confirmadas de la publicación cuyas noches se cruzan con el rango semiabierto [desde, hasta). */
    List<RangoOcupado> confirmadas(PublicacionId publicacionId, LocalDate desde, LocalDate hasta);

    /** Noches ocupadas, en el rango semiabierto [llegada, salida): la salida no es una noche de la estancia. */
    record RangoOcupado(LocalDate llegada, LocalDate salida) {

        public boolean incluye(LocalDate dia) {
            return !dia.isBefore(llegada) && dia.isBefore(salida);
        }
    }
}
