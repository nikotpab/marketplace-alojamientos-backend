package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RevisarPublicacionIT extends PruebaIntegracion {

    private UUID anfitrion;
    private UUID publicacion;
    private String administrador;

    @BeforeEach
    void preparar() {
        anfitrion = crearUsuario(Rol.ANFITRION);
        publicacion = crearPublicacionActiva(anfitrion);
        comoAplicacion.update("UPDATE publicacion SET estado = 'PENDIENTE_REVISION' WHERE id = ?", publicacion);
        administrador = sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR);
    }

    @Test
    void alAprobarlaQuedaActivaVisibleYSeAvisaAlAnfitrion() throws Exception {
        aprobar(publicacion, administrador)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        mvc.perform(get("/api/v1/alojamientos/{id}", publicacion)).andExpect(status().isOk());
        assertThat(auditados("PublicacionAprobada")).isEqualTo(1);
        assertThat(avisosAlAnfitrion("PublicacionAprobada")).isEqualTo(1);
    }

    @Test
    void alRechazarlaVuelveABorradorConElMotivoYSeAvisaAlAnfitrion() throws Exception {
        rechazar(publicacion, "{\"motivo\":\"Faltan fotos del baño\"}", administrador)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.motivoRechazo").value("Faltan fotos del baño"));

        assertThat(auditados("PublicacionRechazada")).isEqualTo(1);
        assertThat(avisosAlAnfitrion("PublicacionRechazada")).isEqualTo(1);
    }

    @Test
    void rechazarSinMotivoNoCambiaElEstado() throws Exception {
        rechazar(publicacion, "{\"motivo\":\"  \"}", administrador)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo").exists());

        assertThat(estado()).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    void soloSeRevisaUnaPublicacionPendiente() throws Exception {
        comoAplicacion.update("UPDATE publicacion SET estado = 'BORRADOR' WHERE id = ?", publicacion);

        aprobar(publicacion, administrador).andExpect(status().isConflict());
        rechazar(publicacion, "{\"motivo\":\"Incompleta\"}", administrador).andExpect(status().isConflict());
    }

    @Test
    void unAnfitrionNoPuedeAprobarNiSiquieraLaSuya() throws Exception {
        aprobar(publicacion, sesionDe(anfitrion, Rol.ANFITRION)).andExpect(status().isForbidden());

        assertThat(estado()).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    void unaPublicacionInexistenteResponde404() throws Exception {
        aprobar(UUID.randomUUID(), administrador).andExpect(status().isNotFound());
    }

    private ResultActions aprobar(UUID id, String sesion) throws Exception {
        return mvc.perform(post("/api/v1/admin/publicaciones/{id}/aprobar", id).header("Authorization", sesion));
    }

    private ResultActions rechazar(UUID id, String cuerpo, String sesion) throws Exception {
        return mvc.perform(post("/api/v1/admin/publicaciones/{id}/rechazar", id).header("Authorization", sesion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private String estado() {
        return comoAplicacion.queryForObject("SELECT estado FROM publicacion WHERE id = ?", String.class, publicacion);
    }

    private long auditados(String accion) {
        return comoAplicacion.queryForObject("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = ?",
                Long.class, publicacion, accion);
    }

    /** El aviso sale por el outbox; el procesador está apagado en las pruebas. */
    private long avisosAlAnfitrion(String tipo) {
        return comoAplicacion.queryForObject("""
                SELECT count(*) FROM evento_pendiente
                WHERE tipo = ? AND payload ->> 'entidadId' = ? AND payload -> 'destinatarios' @> jsonb_build_array(CAST(? AS text))""",
                Long.class, tipo, publicacion.toString(), anfitrion.toString());
    }
}
