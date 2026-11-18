package com.inversioneslr.marketplace.reservas.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservaTest {

    private static final Reserva CONFIRMADA = new Reserva(
            UUID.randomUUID(), "RES-001", UUID.randomUUID(), UUID.randomUUID(),
            LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 5),
            2, EstadoReserva.CONFIRMADA, 4,
            new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("10000"),
            BigDecimal.ZERO, new BigDecimal("110000"));

    @Test
    void unaCancelacionCambiaElEstadoYPreservaElDesglose() {
        Reserva cancelada = CONFIRMADA.cancelar();

        assertThat(cancelada.estado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(cancelada.total()).isEqualByComparingTo(CONFIRMADA.total());
        assertThat(cancelada.id()).isEqualTo(CONFIRMADA.id());
    }

    @Test
    void soloSeCancelaUnaReservaConfirmada() {
        assertThatThrownBy(() -> CONFIRMADA.cancelar().cancelar())
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        e -> assertThat(e.tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO));
    }

    @Test
    void unaCompletacionCambiaElEstadoYPreservaElDesglose() {
        Reserva completada = CONFIRMADA.completar();

        assertThat(completada.estado()).isEqualTo(EstadoReserva.COMPLETADA);
        assertThat(completada.total()).isEqualByComparingTo(CONFIRMADA.total());
        assertThat(completada.id()).isEqualTo(CONFIRMADA.id());
    }

    @Test
    void soloSeCompletaUnaReservaConfirmada() {
        assertThatThrownBy(() -> CONFIRMADA.completar().completar())
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        e -> assertThat(e.tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO));
    }
}
