package com.inversioneslr.marketplace.cancelaciones.aplicacion;

import com.inversioneslr.marketplace.cancelaciones.dominio.RepositorioCancelaciones;
import com.inversioneslr.marketplace.compartido.ReservaId;
import com.inversioneslr.marketplace.reservas.CancelacionesDeReservas;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * El historial de reservas pregunta por sus cancelaciones sin leer la tabla. Depende solo del repositorio,
 * no de ServicioCancelaciones, porque ServicioCancelaciones depende de reservas y se formaría un ciclo de beans.
 */
@Component
class CancelacionesDelHistorial implements CancelacionesDeReservas {

    private final RepositorioCancelaciones cancelaciones;

    CancelacionesDelHistorial(RepositorioCancelaciones cancelaciones) {
        this.cancelaciones = cancelaciones;
    }

    @Override
    public Map<ReservaId, CancelacionResumen> deReservas(Collection<ReservaId> reservaIds) {
        if (reservaIds.isEmpty()) {
            return Map.of();
        }
        return cancelaciones.buscarPorReservas(reservaIds.stream().map(ReservaId::valor).toList()).stream()
                .collect(Collectors.toMap(cancelacion -> new ReservaId(cancelacion.reservaId()),
                        cancelacion -> new CancelacionResumen(cancelacion.fecha(), cancelacion.motivo(),
                                cancelacion.valorDevolucion(), cancelacion.estadoFinal().name())));
    }
}
