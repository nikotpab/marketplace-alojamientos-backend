package com.inversioneslr.marketplace.pagos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CircuitBreakerPagosTest {

    private static final Duration ESPERA_LARGA = Duration.ofMinutes(1);

    private final PasarelaFalsa pasarela = new PasarelaFalsa();

    @Test
    void abreTrasCincoFallasSeguidasSinVolverALlamarALaPasarela() {
        CircuitBreakerPagos circuito = new CircuitBreakerPagos(pasarela, ESPERA_LARGA);
        pasarela.fallaCon(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO);

        for (int intento = 0; intento < 5; intento++) {
            cobrarSinExito(circuito);
        }
        assertThat(pasarela.llamadas).isEqualTo(5);

        assertThatThrownBy(() -> circuito.cobrar(BigDecimal.TEN, "clave"))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO));
        assertThat(pasarela.llamadas).isEqualTo(5);
    }

    @Test
    void unRechazoNoCuentaComoFalla() {
        CircuitBreakerPagos circuito = new CircuitBreakerPagos(pasarela, ESPERA_LARGA);
        pasarela.fallaCon(ExcepcionNegocio.Tipo.PAGO_RECHAZADO);

        for (int intento = 0; intento < 10; intento++) {
            cobrarSinExito(circuito);
        }

        assertThat(pasarela.llamadas).isEqualTo(10);
    }

    @Test
    void unaRespuestaCorrectaEntreFallasNoAbre() {
        CircuitBreakerPagos circuito = new CircuitBreakerPagos(pasarela, ESPERA_LARGA);
        pasarela.fallaCon(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO);
        for (int intento = 0; intento < 4; intento++) {
            cobrarSinExito(circuito);
        }
        pasarela.fallaCon(null);
        circuito.cobrar(BigDecimal.TEN, "clave");
        pasarela.fallaCon(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO);

        cobrarSinExito(circuito);
        cobrarSinExito(circuito);

        assertThat(pasarela.llamadas).isEqualTo(7);
    }

    @Test
    void trasLaEsperaPruebaDeNuevoYSeCierraSiResponde() throws InterruptedException {
        Duration espera = Duration.ofMillis(50);
        CircuitBreakerPagos circuito = new CircuitBreakerPagos(pasarela, espera);
        pasarela.fallaCon(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO);
        for (int intento = 0; intento < 5; intento++) {
            cobrarSinExito(circuito);
        }

        Thread.sleep(espera.multipliedBy(2));
        pasarela.fallaCon(null);

        assertThat(circuito.cobrar(BigDecimal.TEN, "clave")).isEqualTo("referencia");
        assertThat(circuito.cobrar(BigDecimal.TEN, "clave")).isEqualTo("referencia");
    }

    private static void cobrarSinExito(CircuitBreakerPagos circuito) {
        assertThatThrownBy(() -> circuito.cobrar(BigDecimal.TEN, "clave")).isInstanceOf(ExcepcionNegocio.class);
    }

    private static final class PasarelaFalsa implements PasarelaPagos {

        private int llamadas;
        private ExcepcionNegocio.Tipo falla;

        void fallaCon(ExcepcionNegocio.Tipo tipo) {
            falla = tipo;
        }

        @Override
        public String cobrar(BigDecimal monto, String clave) {
            llamadas++;
            if (falla != null) {
                throw new ExcepcionNegocio(falla, "Falla simulada");
            }
            return "referencia";
        }

        @Override
        public void devolver(String referencia, BigDecimal monto) {
        }
    }
}
