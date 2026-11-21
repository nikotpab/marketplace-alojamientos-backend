package com.inversioneslr.marketplace.reservas;

import com.inversioneslr.marketplace.compartido.PublicacionId;
import com.inversioneslr.marketplace.compartido.ReservaId;
import com.inversioneslr.marketplace.compartido.UsuarioId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface FachadaReservas {

    DatosReserva datosReserva(ReservaId reservaId);

    /** Una sola consulta para todas; las que no existen no aparecen. */
    Map<ReservaId, DatosReserva> datosReservas(Collection<ReservaId> reservaIds);

    /** Al salir de CONFIRMADA, la restricción anti-solape deja de aplicar y el periodo queda libre. */
    void marcarCancelada(ReservaId reservaId);

    /** Las reservas futuras se conservan y pasan a revisión manual. */
    void marcarFuturasEnRevision(PublicacionId publicacionId);

    /** Al desbloquear la publicación, sus reservas en revisión vuelven a CONFIRMADA. Se avisa a cada huésped. */
    void restablecerEnRevision(PublicacionId publicacionId);

    /** Huésped de cada reserva de la publicación, para que reseñas no lea la tabla reserva. */
    Map<ReservaId, UsuarioId> huespedPorReserva(PublicacionId publicacionId);

    record DatosReserva(UUID id, UUID huespedId, UUID publicacionId, LocalDate fechaLlegada, EstadoReserva estado,
                        BigDecimal total, BigDecimal tarifaServicio) {
    }
}
