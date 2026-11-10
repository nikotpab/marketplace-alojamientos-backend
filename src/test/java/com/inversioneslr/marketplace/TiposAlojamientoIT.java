package com.inversioneslr.marketplace;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** CU-31 · RF-35 y los catálogos de CU-06. */
class TiposAlojamientoIT extends PruebaIntegracion {

    String administrador;

    @BeforeEach
    void preparar() {
        administrador = sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR);
    }

    /** Los tipos de prueba no deben aparecer en las demás pruebas. */
    @AfterEach
    void limpiar() {
        comoAplicacion.update("DELETE FROM tipo_alojamiento WHERE nombre LIKE 'Prueba %'");
    }

    @Test
    void listaLosTiposActivosConSuCodigo() throws Exception {
        mvc.perform(get("/api/v1/configuracion/tipos-alojamiento").param("activos", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'CABANA')].nombre").value(hasItem("Cabaña")))
                .andExpect(jsonPath("$[*].codigo").value(not(hasItem("GLAMPING"))));
    }

    @Test
    void unTipoNuevoApareceDeInmediatoConUnCodigoSacadoDelNombre() throws Exception {
        guardar(post("/api/v1/admin/tipos-alojamiento"), "Prueba casa rúral", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.activo").value(true));

        mvc.perform(get("/api/v1/configuracion/tipos-alojamiento"))
                .andExpect(jsonPath("$[?(@.codigo == 'PRUEBA_CASA_RURAL')].nombre").value(hasItem("Prueba casa rúral")));
        mvc.perform(get("/api/v1/catalogos"))
                .andExpect(jsonPath("$.tipos[*].nombre").value(hasItem("Prueba casa rúral")));
    }

    @Test
    void unTipoDesactivadoDejaDeOfrecerseYElAdministradorLoSigueViendo() throws Exception {
        String respuesta = guardar(post("/api/v1/admin/tipos-alojamiento"), "Prueba domo", true)
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        guardar(put("/api/v1/admin/tipos-alojamiento/{id}", id), "Prueba domo", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mvc.perform(get("/api/v1/configuracion/tipos-alojamiento"))
                .andExpect(jsonPath("$[*].nombre").value(not(hasItem("Prueba domo"))));
        mvc.perform(get("/api/v1/admin/tipos-alojamiento").header("Authorization", administrador))
                .andExpect(jsonPath("$[?(@.nombre == 'Prueba domo')].activo").value(false));
    }

    @Test
    void elNombreNoSeRepite() throws Exception {
        guardar(post("/api/v1/admin/tipos-alojamiento"), "Prueba choza", true).andExpect(status().isCreated());

        guardar(post("/api/v1/admin/tipos-alojamiento"), "Prueba choza", true).andExpect(status().isConflict());
    }

    @Test
    void editarUnTipoInexistenteResponde404() throws Exception {
        guardar(put("/api/v1/admin/tipos-alojamiento/{id}", UUID.randomUUID()), "Prueba fantasma", true)
                .andExpect(status().isNotFound());
    }

    @Test
    void soloElAdministradorAdministraLosTipos() throws Exception {
        mvc.perform(post("/api/v1/admin/tipos-alojamiento").header("Authorization", sesionDe(crearUsuario(Rol.ANFITRION), Rol.ANFITRION))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Prueba intrusa\",\"activo\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void losCatalogosOfrecenTiposActivosServiciosYPoliticas() throws Exception {
        mvc.perform(get("/api/v1/catalogos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipos[*].nombre").value(hasItem("Cabaña")))
                .andExpect(jsonPath("$.tipos[*].nombre").value(not(hasItem("Glamping"))))
                .andExpect(jsonPath("$.servicios[*].nombre").value(hasItem("WiFi")))
                .andExpect(jsonPath("$.politicas[*].nombre").value(hasItem("Flexible")));
    }

    private ResultActions guardar(MockHttpServletRequestBuilder solicitud, String nombre, boolean activo) throws Exception {
        return mvc.perform(solicitud.header("Authorization", administrador).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"%s\",\"activo\":%s}".formatted(nombre, activo)));
    }
}
