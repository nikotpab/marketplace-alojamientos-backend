package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import java.math.BigDecimal;

/**
 * Cobra sin salir del proceso. Sirve en desarrollo y pruebas, y demuestra el cambio de proveedor. Como Stripe,
 * rechaza los métodos de prueba que simulan un cobro fallido, como {@code pm_card_chargeDeclined}, y aprueba los demás.
 */
class AdaptadorPagosSimulado implements PasarelaPagos {

    private static final String METODOS_QUE_FALLAN = "pm_card_charge";

    @Override
    public String cobrar(BigDecimal monto, String clave, String metodoPago) {
        if (metodoPago != null && metodoPago.startsWith(METODOS_QUE_FALLAN)) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_RECHAZADO, "El pago no fue aprobado.");
        }
        return "simulado-" + clave;
    }

    @Override
    public void devolver(String referencia, BigDecimal monto) {
        // Nada que devolver: el cobro simulado no movió dinero.
    }
}
