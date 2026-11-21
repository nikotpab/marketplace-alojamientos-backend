package com.inversioneslr.marketplace.pagos.dominio;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * No guarda datos de tarjeta: los gestiona la pasarela (RNF-25). {@code metodoPago} es el identificador del método de
 * prueba (por ejemplo {@code pm_card_visa}), o null si se usó el predeterminado.
 */
public record Pago(UUID id, UUID reservaId, String referencia, String claveIdempotencia, BigDecimal monto, Estado estado,
                   String metodoPago) {

    public enum Estado {
        /** Se registró antes de cobrar. Si sigue así, el cobro quedó sin reserva (RNF-12). */
        PENDIENTE,
        APROBADO, RECHAZADO, DEVUELTO
    }
}
