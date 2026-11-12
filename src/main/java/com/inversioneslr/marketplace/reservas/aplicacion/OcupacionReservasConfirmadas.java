package com.inversioneslr.marketplace.reservas.aplicacion;

import com.inversioneslr.marketplace.compartido.PublicacionId;
import com.inversioneslr.marketplace.publicaciones.OcupacionReservas;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * SCRUM-142: publicaciones pregunta por las reservas confirmadas sin leer su tabla. Depende solo del repositorio, no
 * de ServicioReservas, porque ServicioReservas depende de publicaciones y se formaría un ciclo de beans.
 */
@Component
class OcupacionReservasConfirmadas implements OcupacionReservas {

    private final RepositorioReservas reservas;

    OcupacionReservasConfirmadas(RepositorioReservas reservas) {
        this.reservas = reservas;
    }

    @Override
    public List<RangoOcupado> confirmadas(PublicacionId publicacionId, LocalDate desde, LocalDate hasta) {
        return reservas.listarConfirmadasQueSeCruzan(publicacionId.valor(), desde, hasta).stream()
                .map(reserva -> new RangoOcupado(reserva.fechaLlegada(), reserva.fechaSalida()))
                .toList();
    }
}
