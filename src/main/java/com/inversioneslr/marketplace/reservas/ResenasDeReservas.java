package com.inversioneslr.marketplace.reservas;

import com.inversioneslr.marketplace.compartido.ReservaId;
import java.util.Collection;
import java.util.Set;

/**
 * Qué reservas ya tienen reseña, sin leer la tabla de reseñas. Lo implementa el módulo de reseñas, que ya
 * depende de reservas: así no hay ciclo entre los dos (como OcupacionReservas).
 */
public interface ResenasDeReservas {

    /** Una sola consulta para todas. Incluye las reseñas ocultas: una reserva admite una sola reseña. */
    Set<ReservaId> conResena(Collection<ReservaId> reservaIds);
}
