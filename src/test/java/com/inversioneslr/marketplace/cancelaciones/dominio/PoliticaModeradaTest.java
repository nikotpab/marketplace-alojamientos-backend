package com.inversioneslr.marketplace.cancelaciones.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** SCRUM-114 · RF-22: total 330.000, de los que 30.000 son la tarifa de servicio, que no se devuelve. */
class PoliticaModeradaTest {

    private static final LocalDate LLEGADA = LocalDate.of(2030, 6, 20);
    private static final ReservaCancelable RESERVA =
            new ReservaCancelable(LLEGADA, new BigDecimal("330000.00"), new BigDecimal("30000.00"));

    @ParameterizedTest(name = "{0} días antes devuelve {1}")
    @CsvSource({
            "30, 300000",
            "14, 300000",
            "13, 150000",
            "7, 150000",
            "6, 0",
            "0, 0",
            "-1, 0"})
    void devuelveSegunLosDiasQueFaltanParaLaLlegada(int diasAntes, String devolucion) {
        BigDecimal valor = new PoliticaModerada().calcularDevolucion(RESERVA, LLEGADA.minusDays(diasAntes));

        assertThat(valor).isEqualByComparingTo(devolucion);
    }
}
