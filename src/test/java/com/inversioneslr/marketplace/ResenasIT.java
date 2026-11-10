package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ResenasIT extends PruebaIntegracion {

    @Test
    void listaLasResenasVisiblesConSuAutorSinLasOcultas() throws Exception {
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        resenar(publicacion, 5, "Excelente ubicación", false);
        resenar(publicacion, 1, "Contenido ofensivo", true);

        mvc.perform(get("/api/v1/publicaciones/{id}/resenas", publicacion).header("Authorization", sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].autor").value("Nombre A."))
                .andExpect(jsonPath("$[0].calificacion").value(5))
                .andExpect(jsonPath("$[0].comentario").value("Excelente ubicación"))
                .andExpect(jsonPath("$[0].fecha").exists());
    }

    @Test
    void elHuespedResenaUnaEstadiaCompletada() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        UUID reserva = reservar(huesped, publicacion, "COMPLETADA");

        publicar(huesped, reserva, 4, "Muy limpio y cómodo")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autor").value("Nombre A."))
                .andExpect(jsonPath("$.calificacion").value(4))
                .andExpect(jsonPath("$.comentario").value("Muy limpio y cómodo"));

        mvc.perform(get("/api/v1/publicaciones/{id}/resenas", publicacion).header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM auditoria.evento_auditoria WHERE actor_id = ? AND accion = 'ResenaPublicada'",
                Long.class, huesped)).isEqualTo(1);
    }

    @Test
    void soloSeResenaUnaEstadiaCompletada() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID reserva = reservar(huesped, crearPublicacionActiva(crearUsuario(Rol.ANFITRION)), "CONFIRMADA");

        publicar(huesped, reserva, 5, "Todavía no he llegado").andExpect(status().isConflict());
    }

    @Test
    void unaReservaAdmiteUnaSolaResena() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID reserva = reservar(huesped, crearPublicacionActiva(crearUsuario(Rol.ANFITRION)), "COMPLETADA");
        publicar(huesped, reserva, 5, "Excelente").andExpect(status().isCreated());

        publicar(huesped, reserva, 1, "Cambié de opinión").andExpect(status().isConflict());
    }

    @Test
    void otroHuespedNoPuedeResenarUnaReservaAjena() throws Exception {
        UUID reserva = reservar(crearUsuario(Rol.HUESPED), crearPublicacionActiva(crearUsuario(Rol.ANFITRION)), "COMPLETADA");

        publicar(crearUsuario(Rol.HUESPED), reserva, 5, "No estuve ahí").andExpect(status().isForbidden());
    }

    @Test
    void laCalificacionVaDeUnoACincoYElTextoHastaMilCaracteres() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID reserva = reservar(huesped, crearPublicacionActiva(crearUsuario(Rol.ANFITRION)), "COMPLETADA");

        publicar(huesped, reserva, 6, "Demasiado bueno").andExpect(status().isBadRequest());
        publicar(huesped, reserva, 5, "a".repeat(1001)).andExpect(status().isBadRequest());
    }

    private ResultActions publicar(UUID huesped, UUID reserva, int calificacion, String contenido) throws Exception {
        return mvc.perform(post("/api/v1/reservas/{id}/resena", reserva)
                .header("Authorization", sesionDe(huesped, Rol.HUESPED))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"calificacion\":" + calificacion + ",\"contenido\":\"" + contenido + "\"}"));
    }

    private UUID reservar(UUID huesped, UUID publicacion, String estado) {
        return comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, '2029-01-01', '2029-01-03', 1, ?) RETURNING id""", UUID.class, huesped, publicacion, estado);
    }

    private void resenar(UUID publicacion, int calificacion, String comentario, boolean oculta) {
        UUID reserva = reservar(crearUsuario(Rol.HUESPED), publicacion, "COMPLETADA");
        comoAplicacion.update("INSERT INTO resena (reserva_id, calificacion, contenido, oculta) VALUES (?, ?, ?, ?)", reserva, calificacion, comentario, oculta);
    }
}
