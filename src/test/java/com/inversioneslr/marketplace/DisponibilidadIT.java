package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class DisponibilidadIT extends PruebaIntegracion {

    @Autowired
    FachadaPublicaciones fachada;

    private UUID anfitrion;
    private UUID publicacion;

    @BeforeEach
    void preparar() {
        anfitrion = crearUsuario(Rol.ANFITRION);
        publicacion = crearPublicacionActiva(anfitrion);
    }

    @Test
    void cualquieraConsultaLaDeUnaActivaConElRangoInclusivo() throws Exception {
        comoAplicacion.update("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, '2030-03-11', '2030-03-13', 2, 'CONFIRMADA')""", crearUsuario(Rol.HUESPED), publicacion);

        mvc.perform(get("/api/v1/publicaciones/{id}/disponibilidad", publicacion).param("desde", "2029-12-30").param("hasta", "2030-03-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueados", contains("2029-12-30", "2029-12-31")))
                .andExpect(jsonPath("$.reservados", contains("2030-03-11", "2030-03-12")));
    }

    @Test
    void laDeUnaPublicacionNoActivaSoloLaVenElPropietarioYElAdministrador() throws Exception {
        comoAplicacion.update("UPDATE publicacion SET estado = 'BORRADOR' WHERE id = ?", publicacion);

        consultar(null).andExpect(status().isNotFound());
        consultar(sesionDe(crearUsuario(Rol.ANFITRION), Rol.ANFITRION)).andExpect(status().isNotFound());
        consultar(sesionDe(anfitrion, Rol.ANFITRION)).andExpect(status().isOk());
        consultar(sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR)).andExpect(status().isOk());
    }

    @Test
    void elAnfitrionBloqueaYLiberaRangosYLaReservaLoRespeta() throws Exception {
        cambiar("2030-03-10", "2030-03-14", "BLOQUEADO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueados", contains("2030-03-10", "2030-03-11", "2030-03-12", "2030-03-13", "2030-03-14")));
        cambiar("2030-03-12", "2030-03-12", "DISPONIBLE")
                .andExpect(jsonPath("$.bloqueados", contains("2030-03-10", "2030-03-11", "2030-03-13", "2030-03-14")));

        assertThat(fachada.periodoDisponible(publicacion, LocalDate.of(2030, 3, 12), LocalDate.of(2030, 3, 13))).isTrue();
        assertThat(fachada.periodoDisponible(publicacion, LocalDate.of(2030, 3, 12), LocalDate.of(2030, 3, 14))).isFalse();
    }

    @Test
    void laRespuestaCubreDesdeElMesAnteriorHastaElSiguiente() throws Exception {
        comoAplicacion.update("INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo) VALUES (?, '2030-04-20', '2030-04-21', 'BLOQUEADO')", publicacion);

        cambiar("2030-03-10", "2030-03-10", "BLOQUEADO")
                .andExpect(jsonPath("$.bloqueados", contains("2030-03-10", "2030-04-20")));
    }

    @Test
    void liberarFueraDelPeriodoLoExtiendeSinPartirlo() throws Exception {
        cambiar("2030-12-20", "2031-01-10", "DISPONIBLE").andExpect(status().isOk());

        assertThat(fachada.periodoDisponible(publicacion, LocalDate.of(2030, 12, 28), LocalDate.of(2031, 1, 5))).isTrue();
        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM periodo_disponibilidad WHERE publicacion_id = ? AND tipo = 'DISPONIBLE'",
                Long.class, publicacion)).isEqualTo(1);
    }

    @Test
    void noSeCambianDiasConReservasConfirmadas() throws Exception {
        comoAplicacion.update("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, '2030-05-10', '2030-05-12', 2, 'CONFIRMADA')""", crearUsuario(Rol.HUESPED), publicacion);

        cambiar("2030-05-11", "2030-05-20", "BLOQUEADO").andExpect(status().isConflict());
        cambiar("2030-05-12", "2030-05-20", "BLOQUEADO").andExpect(status().isOk());
    }

    @Test
    void rangosInvalidosOPasadosSeRechazan() throws Exception {
        cambiar("2030-03-14", "2030-03-10", "BLOQUEADO").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.hasta").exists());
        cambiar("2020-01-01", "2020-01-05", "BLOQUEADO").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.desde").exists());
        cambiar("2030-01-01", "2031-06-01", "BLOQUEADO").andExpect(status().isBadRequest());
        cambiar("2030-03-10", "2030-03-11", "RESERVADO").andExpect(status().isBadRequest());
    }

    @Test
    void soloElPropietarioCambiaLaDisponibilidad() throws Exception {
        UUID otro = crearUsuario(Rol.ANFITRION);

        mvc.perform(put("/api/v1/publicaciones/{id}/disponibilidad", publicacion).header("Authorization", sesionDe(otro, Rol.ANFITRION))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"desde\":\"2030-03-10\",\"hasta\":\"2030-03-11\",\"estado\":\"BLOQUEADO\"}"))
                .andExpect(status().isForbidden());
        consultar(null).andExpect(jsonPath("$.bloqueados", not(hasItem("2030-03-10"))));
    }

    private ResultActions consultar(String sesion) throws Exception {
        var solicitud = get("/api/v1/publicaciones/{id}/disponibilidad", publicacion).param("desde", "2030-03-01").param("hasta", "2030-03-31");
        return mvc.perform(sesion == null ? solicitud : solicitud.header("Authorization", sesion));
    }

    private ResultActions cambiar(String desde, String hasta, String estado) throws Exception {
        return mvc.perform(put("/api/v1/publicaciones/{id}/disponibilidad", publicacion).header("Authorization", sesionDe(anfitrion, Rol.ANFITRION))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"desde\":\"%s\",\"hasta\":\"%s\",\"estado\":\"%s\"}".formatted(desde, hasta, estado)));
    }
}
