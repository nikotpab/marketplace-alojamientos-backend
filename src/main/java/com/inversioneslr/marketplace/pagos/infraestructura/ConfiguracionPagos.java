package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** El proveedor se elige por variable de entorno (ADR-08). */
@Configuration
public class ConfiguracionPagos {

    @Bean
    PasarelaPagos pasarelaPagos(@Value("${marketplace.pagos.proveedor}") String proveedor,
                                @Value("${marketplace.pagos.stripe-clave-secreta}") String claveStripe) {
        PasarelaPagos adaptador = switch (proveedor) {
            case "stripe" -> new AdaptadorStripe(claveStripe);
            case "simulado" -> new AdaptadorPagosSimulado();
            default -> throw new IllegalArgumentException("Proveedor de pagos desconocido: " + proveedor);
        };
        return new CircuitBreakerPagos(adaptador);
    }
}
