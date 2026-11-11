package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import com.inversioneslr.marketplace.reservas.FachadaReservas;
import com.inversioneslr.marketplace.reservas.aplicacion.ServicioReservas;
import com.inversioneslr.marketplace.reservas.dominio.AdicionalReservado;
import com.inversioneslr.marketplace.reservas.dominio.PeriodoNoDisponibleException;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReservasIT extends PruebaIntegracion {

    private static final LocalDate DIA = LocalDate.of(2030, 5, 10);

    @Autowired
    RepositorioReservas reservas;

    @Autowired
    FachadaReservas fachada;

    @Autowired
    FachadaPublicaciones publicaciones;

    @Autowired
    ServicioReservas servicio;

    @Autowired
    Clock reloj;

    @Test
    void cadaNocheSeCompletanLasReservasCuyaSalidaYaPasoYSeAvisaAlHuesped() {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        LocalDate hoy = LocalDate.now(reloj);
        UUID terminada = insertar(huesped, publicacion, hoy.minusDays(3), hoy.minusDays(1));
        UUID enCurso = insertar(huesped, publicacion, hoy.minusDays(1), hoy.plusDays(1));

        servicio.completarReservasFinalizadas();

        assertThat(fachada.datosReserva(terminada).estado()).isEqualTo(EstadoReserva.COMPLETADA);
        assertThat(fachada.datosReserva(enCurso).estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        esperarEventosDe(terminada);
        assertThat(comoAplicacion.queryForObject(
                "SELECT count(*) FROM notificacion WHERE usuario_id = ? AND tipo = 'ReservaCompletada'", Long.class, huesped)).isEqualTo(1);
    }

    @Test
    void soloSeCompletaUnaReservaConfirmada() {
        Reserva cancelada = reserva(UUID.randomUUID(), UUID.randomUUID(), DIA, DIA.plusDays(1)).cancelar();

        assertThatThrownBy(cancelada::completar)
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO));
    }

    private UUID insertar(UUID huesped, UUID publicacion, LocalDate llegada, LocalDate salida) {
        return comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, ?, ?, 1, 'CONFIRMADA') RETURNING id""", UUID.class, huesped, publicacion, llegada, salida);
    }

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

        var solapada = reserva(huesped, publicacion, DIA.plusDays(1), DIA.plusDays(4));
        List<AdicionalReservado> sinAdicionales = List.of();

        assertThatThrownBy(() -> reservas.guardar(solapada, sinAdicionales))
                .isInstanceOf(PeriodoNoDisponibleException.class);
    }

    @Test
    void entregaLosDatosDeUnaReservaAOtrosModulos() {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        Reserva reserva = reserva(huesped, publicacion, DIA.plusDays(10), DIA.plusDays(13));
        reservas.guardar(reserva, List.of());

        var datos = fachada.datosReserva(reserva.id());

        assertThat(datos.huespedId()).isEqualTo(huesped);
        assertThat(datos.publicacionId()).isEqualTo(publicacion);
        assertThat(datos.fechaLlegada()).isEqualTo(reserva.fechaLlegada());
        assertThat(datos.estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(datos.total()).isEqualByComparingTo(reserva.total());
    }

    @Test
    void unaReservaInexistenteSeInforma() {
        assertThatThrownBy(() -> fachada.datosReserva(UUID.randomUUID()))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.NO_ENCONTRADO));
    }

    @Test
    void cancelarLiberaElPeriodoParaOtraReserva() {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        LocalDate llegada = DIA.plusDays(20);
        Reserva reserva = reserva(huesped, publicacion, llegada, llegada.plusDays(3));
        reservas.guardar(reserva, List.of());
        assertThat(publicaciones.periodoDisponible(publicacion, llegada, llegada.plusDays(3))).isFalse();

        fachada.marcarCancelada(reserva.id());

        assertThat(fachada.datosReserva(reserva.id()).estado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(publicaciones.periodoDisponible(publicacion, llegada, llegada.plusDays(3))).isTrue();
        reservas.guardar(reserva(huesped, publicacion, llegada, llegada.plusDays(3)), List.of());
    }

    @Test
    void soloSeCancelaUnaReservaConfirmada() {
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        Reserva reserva = reserva(crearUsuario(Rol.HUESPED), publicacion, DIA.plusDays(30), DIA.plusDays(33));
        reservas.guardar(reserva, List.of());
        fachada.marcarCancelada(reserva.id());

        assertThatThrownBy(() -> fachada.marcarCancelada(reserva.id()))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO));
    }

    private static Reserva reserva(UUID huesped, UUID publicacion, LocalDate llegada, LocalDate salida) {
        BigDecimal valor = new BigDecimal("100000");
        return new Reserva(UUID.randomUUID(), null, huesped, publicacion, llegada, salida, 2, EstadoReserva.CONFIRMADA, 3,
                valor, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, valor);
    }
}
