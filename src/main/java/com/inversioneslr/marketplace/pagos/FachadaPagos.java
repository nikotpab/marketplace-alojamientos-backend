package com.inversioneslr.marketplace.pagos;

import com.inversioneslr.marketplace.compartido.ReservaId;
import java.math.BigDecimal;
import java.util.UUID;

public interface FachadaPagos {

    /**
     * Antes de cobrar registra el intento como PENDIENTE: si nada lo completa, la reconciliación lo revierte (RNF-12).
     *
     * @return referencia del pago
     * @throws com.inversioneslr.marketplace.compartido.ExcepcionNegocio PAGO_RECHAZADO o PAGO_NO_PROCESADO
     */
    String cobrar(BigDecimal monto, String claveSolicitud);

    /** SEC-03: devuelve un cobro que no llegó a tener reserva porque otra ganó el periodo. */
    void devolverCobroSinReserva(String claveSolicitud, String referencia, BigDecimal monto);

    /**
     * Devuelve todo o parte del pago aprobado de una reserva. Cada pago se devuelve una sola vez.
     *
     * @throws com.inversioneslr.marketplace.compartido.ExcepcionNegocio CONFLICTO si no hay un pago aprobado que devolver,
     *                                                                   o PAGO_NO_PROCESADO si la pasarela falla
     */
    void devolverPagoDeReserva(ReservaId reservaId, BigDecimal monto);

    /** Se llama dentro de la transacción que confirma la reserva (SEC-01, paso 10). */
    void registrarPago(ReservaId reservaId, String referencia, String claveSolicitud, BigDecimal monto);
}
