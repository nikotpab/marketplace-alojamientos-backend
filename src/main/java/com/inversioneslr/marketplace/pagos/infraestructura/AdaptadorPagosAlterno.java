package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Experimento de modificabilidad (ASR-08b): un segundo proveedor simulado, distinto del primero. Tiene sus propias
 * referencias, rechaza los cobros de más de 20 millones y, como Stripe, no cobra dos veces con la misma clave.
 */
class AdaptadorPagosAlterno implements PasarelaPagos {

    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("20000000");

    private final Map<String, String> cobrosPorClave = new HashMap<>();

    @Override
    public synchronized String cobrar(BigDecimal monto, String clave) {
        if (monto.compareTo(MONTO_MAXIMO) > 0) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_RECHAZADO, "El proveedor alterno no aprueba cobros de más de 20 millones.");
        }
        return cobrosPorClave.computeIfAbsent(clave, nueva -> "alterno-" + UUID.randomUUID());
    }

    @Override
    public void devolver(String referencia, BigDecimal monto) {
        if (!referencia.startsWith("alterno-")) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO, "El proveedor alterno no reconoce el pago " + referencia + ".");
        }
    }
}
