package com.inversioneslr.marketplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RecuperarContrasenaIT extends PruebaIntegracion {

    private static final String NUEVA = "Nueva-contrasena-1";

    @Test
    void conElCodigoSeDefineUnaNuevaContrasenaYLaAnteriorDejaDeServir() throws Exception {
        String correo = registrar();

        String codigo = codigoPara(correo);
        restablecer(codigo, NUEVA).andExpect(status().isNoContent());

        iniciarSesion(correo, NUEVA).andExpect(status().isOk());
        iniciarSesion(correo, InicioSesionIT.CONTRASENA).andExpect(status().isUnauthorized());
    }

    @Test
    void elCodigoSoloSirveUnaVez() throws Exception {
        String codigo = codigoPara(registrar());
        restablecer(codigo, NUEVA).andExpect(status().isNoContent());

        restablecer(codigo, "Otra-contrasena-2").andExpect(status().isBadRequest());
    }

    @Test
    void unCorreoInexistenteRecibeLaMismaRespuestaPeroElCodigoNoSirve() throws Exception {
        String codigo = codigoPara(UUID.randomUUID() + "@prueba.co");

        restablecer(codigo, NUEVA).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void laNuevaContrasenaDebeTenerAlMenos8Caracteres() throws Exception {
        restablecer(codigoPara(registrar()), "corta").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.contrasenaNueva").exists());
    }

    private String codigoPara(String correo) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/auth/password/recover").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\"}".formatted(correo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }

    private ResultActions restablecer(String codigo, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"contrasenaNueva\":\"%s\"}".formatted(codigo, contrasena)));
    }

    private String registrar() throws Exception {
        String correo = UUID.randomUUID() + "@prueba.co";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Laura","apellido":"Gómez","correo":"%s","telefono":"3001234567","contrasena":"%s","rol":"HUESPED"}"""
                .formatted(correo, InicioSesionIT.CONTRASENA))).andExpect(status().isCreated());
        return correo;
    }

    private ResultActions iniciarSesion(String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)));
    }
}
