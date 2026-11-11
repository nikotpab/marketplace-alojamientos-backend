package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** CU-03 · RF-03 · SCRUM-110: recuperación de la contraseña con un enlace que llega por correo. */
class RecuperarContrasenaIT extends PruebaIntegracion {

    private static final String NUEVA = "Nueva-contrasena-1";

    @Test
    void conElEnlaceDelCorreoSeDefineUnaNuevaContrasenaYLaAnteriorDejaDeServir() throws Exception {
        String correo = registrar();

        solicitar(correo).andExpect(status().isAccepted()).andExpect(content().string(""));
        String texto = ultimoCorreoDeRecuperacion(correo);
        assertThat(texto).contains("http://localhost:4200/recuperar?token=", "15 minutos");
        restablecer(tokenDelEnlace(texto, "recuperar"), NUEVA).andExpect(status().isNoContent());

        iniciarSesion(correo, NUEVA).andExpect(status().isOk());
        iniciarSesion(correo, InicioSesionIT.CONTRASENA).andExpect(status().isUnauthorized());
    }

    @Test
    void elEnlaceSoloSirveUnaVez() throws Exception {
        String correo = registrar();
        solicitar(correo).andExpect(status().isAccepted());
        String token = tokenDelEnlace(ultimoCorreoDeRecuperacion(correo), "recuperar");

        restablecer(token, NUEVA).andExpect(status().isNoContent());
        restablecer(token, NUEVA).andExpect(status().isBadRequest());
    }

    @Test
    void unCorreoInexistenteRecibeLaMismaRespuestaYNoSeEnviaNada() throws Exception {
        String inexistente = UUID.randomUUID() + "@prueba.co";

        solicitar(inexistente).andExpect(status().isAccepted()).andExpect(content().string(""));

        await().during(Duration.ofMillis(500)).until(() -> correosPara(inexistente).isEmpty());
    }

    @Test
    void unEnlaceVencidoNoSirve() throws Exception {
        String correo = registrar();
        solicitar(correo).andExpect(status().isAccepted());
        String token = tokenDelEnlace(ultimoCorreoDeRecuperacion(correo), "recuperar");
        comoAplicacion.update("""
                UPDATE token_correo SET expira_en = now() - interval '1 minute'
                WHERE usuario_id = (SELECT id FROM usuario WHERE correo = ?) AND proposito = 'RESTABLECER_CONTRASENA'""", correo);

        restablecer(token, NUEVA).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void unEnlaceNuevoSoloSaleUnMinutoDespuesYAnulaElAnterior() throws Exception {
        String correo = registrar();
        solicitar(correo).andExpect(status().isAccepted());
        String primero = tokenDelEnlace(ultimoCorreoDeRecuperacion(correo), "recuperar");

        solicitar(correo).andExpect(status().isAccepted());
        await().during(Duration.ofMillis(500)).until(() -> correosPara(correo).size() == 2);

        comoAplicacion.update("""
                UPDATE token_correo SET creado_en = creado_en - interval '2 minutes'
                WHERE usuario_id = (SELECT id FROM usuario WHERE correo = ?)""", correo);
        solicitar(correo).andExpect(status().isAccepted());
        await().until(() -> correosPara(correo).size() == 3);
        String segundo = tokenDelEnlace(ultimoCorreoDeRecuperacion(correo), "recuperar");

        restablecer(primero, NUEVA).andExpect(status().isBadRequest());
        restablecer(segundo, NUEVA).andExpect(status().isNoContent());
    }

    @Test
    void restablecerLaContrasenaTambienVerificaElCorreo() throws Exception {
        String correo = registrar();
        comoAplicacion.update("UPDATE usuario SET correo_verificado = false WHERE correo = ?", correo);
        solicitar(correo).andExpect(status().isAccepted());

        restablecer(tokenDelEnlace(ultimoCorreoDeRecuperacion(correo), "recuperar"), NUEVA).andExpect(status().isNoContent());

        iniciarSesion(correo, NUEVA).andExpect(status().isOk());
    }

    @Test
    void laNuevaContrasenaDebeTenerAlMenos8Caracteres() throws Exception {
        restablecer("cualquiera", "corta").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.contrasenaNueva").exists());
    }

    /** El registro ya envió el de verificación: espera el segundo, el de recuperación. */
    private String ultimoCorreoDeRecuperacion(String correo) throws Exception {
        await().atMost(Duration.ofSeconds(10)).until(() -> correosPara(correo).size() >= 2);
        String texto = ultimoCorreoPara(correo);
        assertThat(texto).contains("/recuperar?token=");
        return texto;
    }

    private ResultActions solicitar(String correo) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/recover").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\"}".formatted(correo)));
    }

    private ResultActions restablecer(String token, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"contrasenaNueva\":\"%s\"}".formatted(token, contrasena)));
    }

    /** Registrada por la API y verificada, como una cuenta en uso. */
    private String registrar() throws Exception {
        String correo = UUID.randomUUID() + "@prueba.co";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Laura","apellido":"Gómez","correo":"%s","telefono":"3001234567","contrasena":"%s","rol":"HUESPED"}"""
                .formatted(correo, InicioSesionIT.CONTRASENA))).andExpect(status().isCreated());
        await().atMost(Duration.ofSeconds(10)).until(() -> correosPara(correo).size() == 1);
        verificarCorreoDe(correo);
        return correo;
    }

    private ResultActions iniciarSesion(String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)));
    }
}
