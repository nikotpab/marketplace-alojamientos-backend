package com.inversioneslr.marketplace;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsultarPublicacionesIT extends PruebaIntegracion {

    private UUID anfitrion;
    private UUID otroAnfitrion;
    private UUID propia;
    private UUID ajena;

    @BeforeEach
    void preparar() {
        anfitrion = crearUsuario(Rol.ANFITRION);
        otroAnfitrion = crearUsuario(Rol.ANFITRION);
        propia = crearPublicacionActiva(anfitrion);
        ajena = crearPublicacionActiva(otroAnfitrion);
    }

    @Test
    void elAnfitrionSoloVeLasSuyasEnCualquierEstado() throws Exception {
        UUID borrador = crearBorrador(anfitrion);

        mvc.perform(get("/api/v1/publicaciones/mias").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(borrador.toString()))
                .andExpect(jsonPath("$[0].estado").value("BORRADOR"))
                .andExpect(jsonPath("$[1].id").value(propia.toString()))
                .andExpect(jsonPath("$[*].id", not(hasItem(ajena.toString()))));
    }

    @Test
    void elPropietarioYElAdministradorConsultanUnaPublicacion() throws Exception {
        mvc.perform(get("/api/v1/publicaciones/{id}", propia).header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Casa de prueba"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
        mvc.perform(get("/api/v1/publicaciones/{id}", propia).header("Authorization", sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR)))
                .andExpect(status().isOk());
    }

    @Test
    void otroAnfitrionOUnHuespedNoPuedenConsultarlaParaEditar() throws Exception {
        mvc.perform(get("/api/v1/publicaciones/{id}", propia).header("Authorization", sesionDe(otroAnfitrion, Rol.ANFITRION)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/publicaciones/{id}", propia).header("Authorization", sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaPublicacionInexistenteResponde404() throws Exception {
        mvc.perform(get("/api/v1/publicaciones/{id}", UUID.randomUUID()).header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isNotFound());
    }

    @Test
    void elAdministradorVeTodasYPuedeFiltrarPorEstado() throws Exception {
        String administrador = sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR);
        UUID borrador = crearBorrador(anfitrion);

        mvc.perform(get("/api/v1/admin/publicaciones").header("Authorization", administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(propia.toString())))
                .andExpect(jsonPath("$[*].id", hasItem(ajena.toString())))
                .andExpect(jsonPath("$[*].id", hasItem(borrador.toString())))
                .andExpect(jsonPath("$[0].anfitrion").value("Nombre Apellido"));
        mvc.perform(get("/api/v1/admin/publicaciones").param("estado", "BORRADOR").header("Authorization", administrador))
                .andExpect(jsonPath("$[*].id", hasItem(borrador.toString())))
                .andExpect(jsonPath("$[*].estado", not(hasItem("ACTIVA"))));
        mvc.perform(get("/api/v1/admin/publicaciones").param("estado", "INEXISTENTE").header("Authorization", administrador))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloElAdministradorVeTodas() throws Exception {
        mvc.perform(get("/api/v1/admin/publicaciones").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isForbidden());
    }

    private UUID crearBorrador(UUID anfitrionId) {
        return comoAplicacion.queryForObject("""
                INSERT INTO publicacion (anfitrion_id, titulo, estado, actualizado_en)
                VALUES (?, 'Borrador nuevo', 'BORRADOR', now() + interval '1 minute') RETURNING id""", UUID.class, anfitrionId);
    }
}
