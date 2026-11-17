package com.inversioneslr.marketplace.pagos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConfiguracionPagosTest {

    private final ConfiguracionPagos config = new ConfiguracionPagos();

    @Test
    void stripeConClaveValida() {
        var pasarela = config.pasarelaPagos("stripe", "sk_test_clave_de_prueba", "pm_card_visa");

        assertThat(pasarela).isInstanceOf(AdaptadorStripe.class);
    }

    @Test
    void stripeConClaveVaciaLanzaExcepcion() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> config.pasarelaPagos("stripe", "   ", "pm_card_visa"))
                .withMessageContaining("STRIPE_CLAVE_SECRETA");
    }

    @Test
    void simuladoDevuelveAdaptadorSimulado() {
        var pasarela = config.pasarelaPagos("simulado", "", "");

        assertThat(pasarela).isInstanceOf(AdaptadorPagosSimulado.class);
    }

    @Test
    void alternoDevuelveAdaptadorAlterno() {
        var pasarela = config.pasarelaPagos("alterno", "", "");

        assertThat(pasarela).isInstanceOf(AdaptadorPagosAlterno.class);
    }

    @Test
    void proveedorDesconocidoLanzaExcepcion() {
        assertThatThrownBy(() -> config.pasarelaPagos("inexistente", "", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inexistente");
    }
}
