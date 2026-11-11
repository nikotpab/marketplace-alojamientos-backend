package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class InicioSesionIT extends PruebaIntegracion {

    static final String CONTRASENA = "Contrasena-de-prueba-1";

    @Test
    void conCredencialesCorrectasEntregaUnTokenQueAbreLasRutasProtegidas() throws Exception {
        String correo = registrar("HUESPED");

        String respuesta = iniciarSesion(correo, CONTRASENA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.correo").value(correo))
                .andExpect(jsonPath("$.usuario.nombre").value("Laura"))
                .andExpect(jsonPath("$.usuario.rol").value("HUESPED"))
                .andExpect(jsonPath("$.usuario.id").exists())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(respuesta, "$.token");
        mvc.perform(get("/api/v1/notificaciones").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void elCorreoNoDistingueMayusculas() throws Exception {
        String correo = registrar("ANFITRION");

        iniciarSesion(correo.toUpperCase(), CONTRASENA).andExpect(status().isOk());
    }

    @Test
    void unaContrasenaIncorrectaResponde401YQuedaRegistrada() throws Exception {
        String correo = registrar("HUESPED");

        iniciarSesion(correo, "otra-contrasena")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

        UUID usuario = comoAplicacion.queryForObject("SELECT id FROM usuario WHERE correo = ?", UUID.class, correo);
        assertThat(comoAplicacion.queryForObject(
                "SELECT count(*) FROM auditoria.evento_auditoria WHERE actor_id = ? AND accion = 'InicioSesionFallido'", Long.class, usuario)).isEqualTo(1);
    }

    @Test
    void unCorreoInexistenteRespondeIgualQueUnaContrasenaIncorrecta() throws Exception {
        iniciarSesion(UUID.randomUUID() + "@prueba.co", CONTRASENA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void unaCuentaRestringidaNoPuedeIniciarSesion() throws Exception {
        String correo = registrar("HUESPED");
        comoAplicacion.update("UPDATE usuario SET restringido = true WHERE correo = ?", correo);

        iniciarSesion(correo, CONTRASENA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_RESTRINGIDA"));
    }

    @Test
    void unaCuentaDesactivadaDesdeElPerfilNoPuedeIniciarSesion() throws Exception {
        String correo = registrar("HUESPED");
        String token = JsonPath.read(iniciarSesion(correo, CONTRASENA).andReturn().getResponse().getContentAsString(), "$.token");
        mvc.perform(patch("/api/v1/usuarios/me/estado").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isOk());

        iniciarSesion(correo, CONTRASENA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"));
    }

    @Test
    void cincoIntentosFallidosSeguidosBloqueanLaCuentaAunqueLuegoLaContrasenaSeaCorrecta() throws Exception {
        String correo = registrar("HUESPED");
        for (int intento = 1; intento <= 4; intento++) {
            iniciarSesion(correo, "otra-contrasena").andExpect(status().isUnauthorized());
        }

        iniciarSesion(correo, "otra-contrasena").andExpect(status().isLocked()).andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));
        iniciarSesion(correo, CONTRASENA).andExpect(status().isLocked());
    }

    @Test
    void unInicioCorrectoReiniciaElConteoDeIntentos() throws Exception {
        String correo = registrar("HUESPED");
        for (int intento = 1; intento <= 4; intento++) {
            iniciarSesion(correo, "otra-contrasena").andExpect(status().isUnauthorized());
        }
        iniciarSesion(correo, CONTRASENA).andExpect(status().isOk());

        iniciarSesion(correo, "otra-contrasena").andExpect(status().isUnauthorized());
    }

    String registrar(String rol) throws Exception {
        String correo = UUID.randomUUID() + "@prueba.co";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Laura","apellido":"Gómez","correo":"%s","telefono":"3001234567","contrasena":"%s","rol":"%s"}"""
                .formatted(correo, CONTRASENA, rol))).andExpect(status().isCreated());
        verificarCorreoDe(correo);
        return correo;
    }

    ResultActions iniciarSesion(String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)));
    }
}
