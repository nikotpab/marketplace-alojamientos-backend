package com.inversioneslr.marketplace.pagos;

import java.math.BigDecimal;
import java.util.UUID;

public interface FachadaPagos {

    /**
     * @return referencia del pago
     * @throws com.inversioneslr.marketplace.compartido.ExcepcionNegocio PAGO_RECHAZADO o PAGO_NO_PROCESADO
     */
    String cobrar(BigDecimal monto, String claveSolicitud);

    void devolver(String referencia, BigDecimal monto);

    /**
     * Devuelve todo o parte del pago aprobado de una reserva. Cada pago se devuelve una sola vez.
     *
     * @throws com.inversioneslr.marketplace.compartido.ExcepcionNegocio CONFLICTO si no hay un pago aprobado que devolver,
     *                                                                   o PAGO_NO_PROCESADO si la pasarela falla
     */
    void devolverPagoDeReserva(UUID reservaId, BigDecimal monto);

    /** Se llama dentro de la transacción que confirma la reserva (SEC-01, paso 10). */
    void registrarPago(UUID reservaId, String referencia, String claveSolicitud, BigDecimal monto);
}
