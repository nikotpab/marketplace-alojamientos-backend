package com.inversioneslr.marketplace.reservas;

import com.inversioneslr.marketplace.compartido.PublicacionId;
import com.inversioneslr.marketplace.compartido.ReservaId;
import com.inversioneslr.marketplace.compartido.UsuarioId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface FachadaReservas {

    DatosReserva datosReserva(ReservaId reservaId);

    /** Al salir de CONFIRMADA, la restricción anti-solape deja de aplicar y el periodo queda libre (RF-13). */
    void marcarCancelada(ReservaId reservaId);

    /** CU-24: las reservas futuras se conservan y pasan a revisión manual. */
    void marcarFuturasEnRevision(PublicacionId publicacionId);

    /** SCRUM-142: huésped de cada reserva de la publicación, para que reseñas no lea la tabla reserva. */
    Map<ReservaId, UsuarioId> huespedPorReserva(PublicacionId publicacionId);

    record DatosReserva(UUID id, UUID huespedId, UUID publicacionId, LocalDate fechaLlegada, EstadoReserva estado,
                        BigDecimal total, BigDecimal tarifaServicio) {
    }
}
