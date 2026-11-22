package com.inversioneslr.marketplace.resenas.aplicacion;

import com.inversioneslr.marketplace.compartido.ReservaId;
import com.inversioneslr.marketplace.resenas.dominio.RepositorioResenas;
import com.inversioneslr.marketplace.reservas.ResenasDeReservas;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * El historial de reservas pregunta cuáles ya tienen reseña sin leer la tabla. Depende solo del repositorio,
 * no de ServicioResenias, porque ServicioResenias depende de reservas y se formaría un ciclo de beans.
 */
@Component
class ResenasDelHistorial implements ResenasDeReservas {

    private final RepositorioResenas resenas;

    ResenasDelHistorial(RepositorioResenas resenas) {
        this.resenas = resenas;
    }

    @Override
    public Set<ReservaId> conResena(Collection<ReservaId> reservaIds) {
        if (reservaIds.isEmpty()) {
            return Set.of();
        }
        return resenas.reservasConResena(reservaIds.stream().map(ReservaId::valor).toList()).stream()
                .map(ReservaId::new)
                .collect(Collectors.toSet());
    }
}
