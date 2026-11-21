package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Reportar reseñas y verlas en Moderación con su número de reportes. */
class ReportesResenasIT extends PruebaIntegracion {

    UUID autor;
    UUID publicacion;
    LocalDate siguienteLlegada = LocalDate.of(2030, 1, 1);
    UUID resena;
    String administrador;

    @BeforeEach
    void preparar() {
        autor = crearUsuario(Rol.HUESPED);
        publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        resena = resenar(autor, "Pésimo, no vayan");
        administrador = sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR);
    }

    @Test
    void unUsuarioReportaUnaResenaYQuedaEnLaAuditoria() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);

        reportar(huesped, Rol.HUESPED, "{\"motivo\":\"OFENSIVA\",\"comentario\":\"  Insulta al anfitrión  \"}").andExpect(status().isNoContent());

        assertThat(comoAplicacion.queryForMap("SELECT motivo, comentario FROM reporte_resena WHERE resena_id = ? AND usuario_id = ?", resena, huesped))
                .containsEntry("motivo", "OFENSIVA").containsEntry("comentario", "Insulta al anfitrión");
        assertThat(comoAplicacion.queryForObject("""
                SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'ResenaReportada' AND actor_id = ?""",
                Long.class, resena, huesped)).isEqualTo(1);
    }

    @Test
    void unAnfitrionTambienPuedeReportarSinComentario() throws Exception {
        reportar(crearUsuario(Rol.ANFITRION), Rol.ANFITRION, "{\"motivo\":\"INFORMACION_FALSA\"}").andExpect(status().isNoContent());
    }

    @Test
    void reportarDosVecesLaMismaResenaRespondeConflicto() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        reportar(huesped, Rol.HUESPED, "{\"motivo\":\"SPAM\"}").andExpect(status().isNoContent());

        reportar(huesped, Rol.HUESPED, "{\"motivo\":\"OTRO\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya reportaste esta reseña."));
    }

    @Test
    void elAutorNoPuedeReportarSuPropiaResena() throws Exception {
        reportar(autor, Rol.HUESPED, "{\"motivo\":\"SPAM\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No puedes reportar tu propia reseña."));
    }

    @Test
    void elMotivoEsObligatorioYDebeSerDeLaLista() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);

        reportar(huesped, Rol.HUESPED, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.motivo").value("Elige el motivo del reporte."));
        reportar(huesped, Rol.HUESPED, "{\"motivo\":\"NO_ME_GUSTA\"}").andExpect(status().isBadRequest());
        reportar(huesped, Rol.HUESPED, "{\"motivo\":\"OTRO\",\"comentario\":\"" + "x".repeat(501) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void unaResenaOcultaOInexistenteNoSeReporta() throws Exception {
        comoAplicacion.update("UPDATE resena SET oculta = true WHERE id = ?", resena);

        reportar(crearUsuario(Rol.HUESPED), Rol.HUESPED, "{\"motivo\":\"SPAM\"}").andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/resenas/{id}/reportes", UUID.randomUUID())
                        .header("Authorization", sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"SPAM\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moderacionListaLasReportadasConSuNumeroDeReportesYOcultarlaLaQuita() throws Exception {
        UUID otra = resenar(crearUsuario(Rol.HUESPED), "Muy bien");
        UUID sinReportes = resenar(crearUsuario(Rol.HUESPED), "Sin reportes");
        reportar(crearUsuario(Rol.HUESPED), Rol.HUESPED, "{\"motivo\":\"OFENSIVA\"}").andExpect(status().isNoContent());
        reportar(crearUsuario(Rol.ANFITRION), Rol.ANFITRION, "{\"motivo\":\"SPAM\"}").andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/resenas/{id}/reportes", otra).header("Authorization", sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"OTRO\"}")).andExpect(status().isNoContent());

        // La lista es de toda la plataforma: otras pruebas también dejan reseñas reportadas.
        String reportada = "$[?(@.id == '" + resena + "')]";
        mvc.perform(get("/api/v1/admin/resenas/reportadas").header("Authorization", administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath(reportada + ".reportes").value(contains(2)))
                .andExpect(jsonPath(reportada + ".motivos[*]").value(contains("OFENSIVA", "SPAM")))
                .andExpect(jsonPath(reportada + ".publicacionId").value(contains(publicacion.toString())))
                .andExpect(jsonPath(reportada + ".autor").value(contains("Nombre A.")))
                .andExpect(jsonPath(reportada + ".comentario").value(contains("Pésimo, no vayan")))
                .andExpect(jsonPath("$[?(@.id == '" + otra + "')].reportes").value(contains(1)))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(sinReportes.toString()))));

        mvc.perform(post("/api/v1/admin/resenas/{id}/ocultamiento", resena).header("Authorization", administrador))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/admin/resenas/reportadas").header("Authorization", administrador))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(resena.toString()))))
                .andExpect(jsonPath("$[*].id").value(hasItem(otra.toString())));
    }

    private ResultActions reportar(UUID usuario, Rol rol, String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/resenas/{id}/reportes", resena)
                .header("Authorization", sesionDe(usuario, rol))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** Una reseña de una estadía completada del huésped en la publicación. */
    private UUID resenar(UUID huesped, String contenido) {
        LocalDate llegada = siguienteLlegada;
        siguienteLlegada = siguienteLlegada.plusDays(3);
        UUID reserva = comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, ?, ?, 1, 'COMPLETADA') RETURNING id""", UUID.class, huesped, publicacion, llegada, llegada.plusDays(2));
        return comoAplicacion.queryForObject("INSERT INTO resena (reserva_id, calificacion, contenido) VALUES (?, 1, ?) RETURNING id",
                UUID.class, reserva, contenido);
    }
}
