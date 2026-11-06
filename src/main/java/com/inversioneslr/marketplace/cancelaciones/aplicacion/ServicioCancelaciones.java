package com.inversioneslr.marketplace.cancelaciones.aplicacion;

import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion;
import com.inversioneslr.marketplace.cancelaciones.dominio.RepositorioCancelaciones;
import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.pagos.api.FachadaPagos;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones;
import com.inversioneslr.marketplace.reservas.api.FachadaReservas;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ServicioCancelaciones {

    private final RepositorioCancelaciones cancelaciones;
    private final FachadaReservas reservas;
    private final FachadaPublicaciones publicaciones;
    private final FachadaPagos pagos;
    private final PublicadorEventos eventos;
    private final TransactionTemplate transaccion;

    public ServicioCancelaciones(RepositorioCancelaciones cancelaciones, FachadaReservas reservas,
                                 FachadaPublicaciones publicaciones, FachadaPagos pagos, PublicadorEventos eventos,
                                 TransactionTemplate transaccion) {
        this.cancelaciones = cancelaciones;
        this.reservas = reservas;
        this.publicaciones = publicaciones;
        this.pagos = pagos;
        this.eventos = eventos;
        this.transaccion = transaccion;
    }

    /** CU-20, paso 2: muestra la devolución antes de pedir la confirmación. */
    public BigDecimal estimarDevolucion(UUID huespedId, UUID reservaId) {
        throw new UnsupportedOperationException("CU-20");
    }

    /**
     * CU-20 · SEC-04.
     * <ol>
     * <li>Validar que la reserva sea del huésped y esté CONFIRMADA.</li>
     * <li>Calcular la devolución con la política de la publicación.</li>
     * <li>Solicitar la devolución fuera de la transacción.</li>
     * <li>En una sola transacción: reserva CANCELADA, cancelación, auditoría y evento.</li>
     * </ol>
     */
    public Cancelacion cancelarReserva(UUID huespedId, UUID reservaId, CancelarReservaComando comando) {
        throw new UnsupportedOperationException("CU-20");
    }

    public record CancelarReservaComando(@NotBlank(message = "Indica el motivo de la cancelación.") String motivo) {
    }
}
