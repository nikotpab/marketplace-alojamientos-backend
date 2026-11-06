package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import java.math.BigDecimal;

/** Aprueba todo sin salir del proceso. Sirve en desarrollo y pruebas, y demuestra el cambio de proveedor (ADR-08). */
class AdaptadorPagosSimulado implements PasarelaPagos {

    @Override
    public String cobrar(BigDecimal monto, String clave) {
        return "simulado-" + clave;
    }

    @Override
    public void devolver(String referencia, BigDecimal monto) {
    }
}
