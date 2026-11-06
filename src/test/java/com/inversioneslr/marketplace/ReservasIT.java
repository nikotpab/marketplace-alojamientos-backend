package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import com.inversioneslr.marketplace.reservas.dominio.AdicionalReservado;
import com.inversioneslr.marketplace.reservas.dominio.PeriodoNoDisponibleException;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReservasIT extends PruebaIntegracion {

    private static final LocalDate DIA = LocalDate.of(2030, 5, 10);

    @Autowired
    RepositorioReservas reservas;

    @Test
    void guardaLaReservaConSusServiciosAdicionales() {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        UUID adicional = comoAplicacion.queryForObject(
                "INSERT INTO servicio_adicional (publicacion_id, nombre, costo) VALUES (?, 'Desayuno', 120000) RETURNING id", UUID.class, publicacion);
        Reserva reserva = reserva(huesped, publicacion, DIA, DIA.plusDays(3));

        reservas.guardar(reserva, List.of(new AdicionalReservado(adicional, new BigDecimal("120000"))));

        assertThat(comoAplicacion.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, reserva.id())).isEqualTo("CONFIRMADA");
        assertThat(comoAplicacion.queryForObject("SELECT costo_aplicado FROM reserva_servicio_adicional WHERE reserva_id = ?", BigDecimal.class, reserva.id()))
                .isEqualByComparingTo("120000");
    }

    @Test
    void elRechazoDeLaRestriccionSeTraduceAPeriodoNoDisponible() {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        reservas.guardar(reserva(huesped, publicacion, DIA, DIA.plusDays(3)), List.of());

        assertThatThrownBy(() -> reservas.guardar(reserva(huesped, publicacion, DIA.plusDays(1), DIA.plusDays(4)), List.of()))
                .isInstanceOf(PeriodoNoDisponibleException.class);
    }

    private static Reserva reserva(UUID huesped, UUID publicacion, LocalDate llegada, LocalDate salida) {
        BigDecimal valor = new BigDecimal("100000");
        return new Reserva(UUID.randomUUID(), huesped, publicacion, llegada, salida, 2, EstadoReserva.CONFIRMADA, 3,
                valor, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, valor);
    }
}
