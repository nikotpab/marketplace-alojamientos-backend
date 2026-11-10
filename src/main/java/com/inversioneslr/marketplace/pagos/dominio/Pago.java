package com.inversioneslr.marketplace.pagos.dominio;

import java.math.BigDecimal;
import java.util.UUID;

/** No guarda datos de tarjeta: los gestiona la pasarela (RNF-25). */
public record Pago(UUID id, UUID reservaId, String referencia, String claveIdempotencia, BigDecimal monto, Estado estado) {

    public enum Estado {
        /** Se registró antes de cobrar. Si sigue así, el cobro quedó sin reserva (RNF-12). */
        PENDIENTE,
        APROBADO, RECHAZADO, DEVUELTO
    }
}
