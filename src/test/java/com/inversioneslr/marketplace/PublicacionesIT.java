package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.dominio.Publicacion;
import com.inversioneslr.marketplace.publicaciones.dominio.RepositorioPublicaciones;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PublicacionesIT extends PruebaIntegracion {

    private static final LocalDate DIA = LocalDate.of(2030, 3, 10);
    private static final LocalDate FUERA_DEL_PERIODO = LocalDate.of(2031, 3, 10);

    @Autowired
    FachadaPublicaciones publicaciones;

    @Autowired
    RepositorioPublicaciones repositorio;

    @Test
    void entregaLosDatosNecesariosParaReservar() {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID publicacion = crearPublicacionActiva(anfitrion);
        comoAplicacion.update("INSERT INTO servicio_adicional (publicacion_id, nombre, costo) VALUES (?, 'Desayuno', 120000)", publicacion);

        var datos = publicaciones.datosPublicacion(publicacion);

        assertThat(datos.anfitrionId()).isEqualTo(anfitrion);
        assertThat(datos.activa()).isTrue();
        assertThat(datos.capacidad()).isEqualTo(4);
        assertThat(datos.precioNoche()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(datos.politicaCancelacion()).isEqualTo("Flexible");
        assertThat(datos.serviciosAdicionales()).singleElement().satisfies(adicional -> assertThat(adicional.nombre()).isEqualTo("Desayuno"));
    }

    @Test
    void guardaYRecuperaUnaPublicacionConSusDetalles() {
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        UUID servicio = comoAplicacion.queryForObject("SELECT id FROM servicio LIMIT 1", UUID.class);
        Publicacion actual = repositorio.buscarPorId(publicacion).orElseThrow();
        Publicacion editada = new Publicacion(actual.id(), actual.anfitrionId(), actual.tipoAlojamientoId(), actual.politicaCancelacionId(),
                "Título nuevo", "Descripción", "Medellín", "El Poblado", 6, new BigDecimal("250000.00"),
                List.of(servicio), List.of("No se permiten fiestas"), List.of("a.jpg", "b.jpg"), actual.estado(), null);

        repositorio.guardar(editada);

        assertThat(repositorio.buscarPorId(publicacion)).contains(editada);
    }

    @Test
    void publicacionInexistenteSeInforma() {
        assertThatThrownBy(() -> publicaciones.datosPublicacion(UUID.randomUUID()))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.NO_ENCONTRADO));
    }

    @Test
    void elPeriodoDebeEstarDentroDeUnoDisponible() {
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));

        assertThat(publicaciones.periodoDisponible(publicacion, DIA, DIA.plusDays(3))).isTrue();
        assertThat(publicaciones.periodoDisponible(publicacion, FUERA_DEL_PERIODO, FUERA_DEL_PERIODO.plusDays(3))).isFalse();
    }

    @Test
    void unPeriodoBloqueadoNoEstaDisponible() {
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        comoAplicacion.update("INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo) VALUES (?, ?, ?, 'BLOQUEADO')",
                publicacion, DIA.plusDays(1), DIA.plusDays(2));

        assertThat(publicaciones.periodoDisponible(publicacion, DIA, DIA.plusDays(3))).isFalse();
    }

    @Test
    void unaReservaConfirmadaOcupaElPeriodoPeroUnaCanceladaNo() {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        String insertar = "INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado) VALUES (?, ?, ?, ?, 1, ?)";

        comoAplicacion.update(insertar, huesped, publicacion, DIA, DIA.plusDays(3), "CANCELADA");
        assertThat(publicaciones.periodoDisponible(publicacion, DIA, DIA.plusDays(3))).isTrue();

        comoAplicacion.update(insertar, huesped, publicacion, DIA, DIA.plusDays(3), "CONFIRMADA");
        assertThat(publicaciones.periodoDisponible(publicacion, DIA, DIA.plusDays(3))).isFalse();
        // La salida es libre para la siguiente llegada.
        assertThat(publicaciones.periodoDisponible(publicacion, DIA.plusDays(3), DIA.plusDays(5))).isTrue();
    }
}
