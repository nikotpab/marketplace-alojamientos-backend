package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class PausarPublicacionIT extends PruebaIntegracion {

    private UUID anfitrion;
    private UUID publicacion;

    @BeforeEach
    void preparar() {
        anfitrion = crearUsuario(Rol.ANFITRION);
        publicacion = crearPublicacionActiva(anfitrion);
    }

    @Test
    void unaActivaSePausaYSeReactivaQuedandoEnAuditoria() throws Exception {
        accion(publicacion, "pausar", anfitrion).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PAUSADA"));
        accion(publicacion, "reactivar", anfitrion).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVA"));

        assertThat(comoAplicacion.queryForObject("""
                SELECT count(*) FROM auditoria.evento_auditoria
                WHERE entidad_id = ? AND accion IN ('PublicacionPausada', 'PublicacionReactivada')""", Long.class, publicacion)).isEqualTo(2);
    }

    @Test
    void unaPausadaDejaDeMostrarseAlPublico() throws Exception {
        accion(publicacion, "pausar", anfitrion);

        mvc.perform(get("/api/v1/alojamientos/{id}", publicacion))
                .andExpect(status().isNotFound());
    }

    @Test
    void soloSePausaUnaActivaYSoloSeReactivaUnaPausada() throws Exception {
        accion(publicacion, "reactivar", anfitrion).andExpect(status().isConflict());
        accion(publicacion, "pausar", anfitrion);
        accion(publicacion, "pausar", anfitrion).andExpect(status().isConflict());
    }

    @Test
    void elAnfitrionNoPuedeReactivarUnaBloqueada() throws Exception {
        comoAplicacion.update("UPDATE publicacion SET estado = 'BLOQUEADA', motivo_estado = 'Fraude' WHERE id = ?", publicacion);

        accion(publicacion, "reactivar", anfitrion)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Una publicación bloqueada solo la puede desbloquear un administrador."));
    }

    @Test
    void otroAnfitrionNoPuedePausarla() throws Exception {
        accion(publicacion, "pausar", crearUsuario(Rol.ANFITRION)).andExpect(status().isForbidden());
    }

    private ResultActions accion(UUID id, String accion, UUID usuario) throws Exception {
        return mvc.perform(post("/api/v1/publicaciones/{id}/" + accion, id).header("Authorization", sesionDe(usuario, Rol.ANFITRION)));
    }
}
