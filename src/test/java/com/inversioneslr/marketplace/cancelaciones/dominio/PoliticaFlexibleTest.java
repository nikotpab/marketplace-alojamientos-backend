package com.inversioneslr.marketplace.cancelaciones.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** RF-23: total 330.000, de los que 30.000 son la tarifa de servicio, que no se devuelve. */
class PoliticaFlexibleTest {

    private static final LocalDate LLEGADA = LocalDate.of(2030, 6, 20);
    private static final ReservaCancelable RESERVA =
            new ReservaCancelable(LLEGADA, new BigDecimal("330000.00"), new BigDecimal("30000.00"));

    @ParameterizedTest(name = "{0} días antes devuelve {1}")
    @CsvSource({
            "30, 300000",
            "5, 300000",
            "4, 150000",
            "2, 150000",
            "1, 0",
            "0, 0",
            "-1, 0"})
    void devuelveSegunLosDiasQueFaltanParaLaLlegada(int diasAntes, String devolucion) {
        BigDecimal valor = new PoliticaFlexible().calcularDevolucion(RESERVA, LLEGADA.minusDays(diasAntes));

        assertThat(valor).isEqualByComparingTo(devolucion);
    }

    @Test
    void laMitadSeRedondeaAlPeso() {
        ReservaCancelable impar = new ReservaCancelable(LLEGADA, new BigDecimal("100001.00"), BigDecimal.ZERO);

        assertThat(new PoliticaFlexible().calcularDevolucion(impar, LLEGADA.minusDays(3))).isEqualByComparingTo("50001");
    }

    @Test
    void unaPoliticaSinReglasResponde409() {
        assertThatThrownBy(() -> PoliticaCancelacion.porNombre("Estricta"))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, error -> {
                    assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO);
                    assertThat(error.getMessage()).contains("Estricta", "no tiene reglas");
                });
    }
}
