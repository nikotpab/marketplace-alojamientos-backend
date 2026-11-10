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

    /** Se llama dentro de la transacción que confirma la reserva (SEC-01, paso 10). */
    void registrarPago(UUID reservaId, String referencia, String claveSolicitud, BigDecimal monto);
}
