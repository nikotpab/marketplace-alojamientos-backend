package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/** El proveedor se elige por variable de entorno (ADR-08). */
@Configuration
public class ConfiguracionPagos {

    @Bean
    CircuitBreakerPagos pasarelaPagos(@Value("${marketplace.pagos.proveedor}") String proveedor,
                                @Value("${marketplace.pagos.stripe-clave-secreta}") String claveStripe,
                                @Value("${marketplace.pagos.stripe-metodo-pago}") String metodoPago) {
        PasarelaPagos adaptador = switch (proveedor) {
            case "stripe" -> adaptadorStripe(claveStripe, metodoPago);
            case "simulado" -> new AdaptadorPagosSimulado();
            case "alterno" -> new AdaptadorPagosAlterno();
            default -> throw new IllegalArgumentException("Proveedor de pagos desconocido: " + proveedor);
        };
        return new CircuitBreakerPagos(adaptador);
    }

    private static AdaptadorStripe adaptadorStripe(String claveSecreta, String metodoPago) {
        Assert.hasText(claveSecreta, "Falta STRIPE_CLAVE_SECRETA");
        return new AdaptadorStripe(AdaptadorStripe.cliente(claveSecreta).build(), metodoPago);
    }
}
