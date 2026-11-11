package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.ResultActions;

/** SCRUM-109: verificación del correo al registrarse. */
@ExtendWith(OutputCaptureExtension.class)
class VerificacionCorreoIT extends PruebaIntegracion {

    private static final String CONTRASENA = "Contrasena-de-prueba-1";

    @Autowired
    JavaMailSenderImpl servidorDeCorreo;

    /** Una clave mal escrita en application.yml no da error: JavaMail la ignora y en producción falla la autenticación. */
    @Test
    void laConfiguracionSmtpCargaLaSeguridadYLosTiemposDeEspera() {
        assertThat(servidorDeCorreo.getJavaMailProperties())
                .containsEntry("mail.smtp.auth", "false")
                .containsEntry("mail.smtp.starttls.enable", "false")
                .containsEntry("mail.smtp.starttls.required", "false")
                .containsEntry("mail.smtp.connectiontimeout", "5000")
                .containsEntry("mail.smtp.timeout", "5000")
                .containsEntry("mail.smtp.writetimeout", "5000");
        assertThat(servidorDeCorreo.getDefaultEncoding()).isEqualTo("UTF-8");
    }

    @Test
    void alRegistrarseLlegaUnEnlaceYHastaUsarloNoSePuedeIniciarSesion(CapturedOutput salida) throws Exception {
        String correo = registrar();

        String texto = ultimoCorreoPara(correo);
        assertThat(texto).contains("Hola, Laura", "http://localhost:4200/verificar-correo?token=", "24 horas");
        String token = tokenDelEnlace(texto, "verificar-correo");

        iniciarSesion(correo, CONTRASENA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CORREO_NO_VERIFICADO"));
        verificar(token).andExpect(status().isNoContent());
        iniciarSesion(correo, CONTRASENA).andExpect(status().isOk());

        // El token no queda en los logs ni en la auditoría.
        assertThat(salida.getOut()).doesNotContain(token);
        assertThat(comoAplicacion.queryForObject(
                "SELECT count(*) FROM auditoria.evento_auditoria WHERE detalle::text LIKE ?", Long.class, "%" + token + "%")).isZero();
        assertThat(comoAplicacion.queryForObject("""
                SELECT count(*) FROM auditoria.evento_auditoria
                WHERE accion = 'CorreoVerificado' AND actor_id = (SELECT id FROM usuario WHERE correo = ?)""", Long.class, correo)).isEqualTo(1);
    }

    @Test
    void elEnlaceSirveUnaSolaVez() throws Exception {
        String token = tokenDelEnlace(ultimoCorreoPara(registrar()), "verificar-correo");

        verificar(token).andExpect(status().isNoContent());
        verificar(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.token").exists());
    }

    @Test
    void conUnaContrasenaIncorrectaNoSeRevelaSiElCorreoEstaVerificado() throws Exception {
        String correo = registrar();

        iniciarSesion(correo, "Otra-contrasena-9")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void unEnlaceInventadoOVencidoNoSirve() throws Exception {
        String correo = registrar();
        String token = tokenDelEnlace(ultimoCorreoPara(correo), "verificar-correo");
        comoAplicacion.update("""
                UPDATE token_correo SET expira_en = now() - interval '1 minute'
                WHERE usuario_id = (SELECT id FROM usuario WHERE correo = ?)""", correo);

        verificar(token).andExpect(status().isBadRequest());
        verificar("inventado").andExpect(status().isBadRequest());
    }

    @Test
    void unEnlaceNuevoSoloSaleUnMinutoDespuesYAnulaElAnterior() throws Exception {
        String correo = registrar();
        String primero = tokenDelEnlace(ultimoCorreoPara(correo), "verificar-correo");

        reenviar(correo).andExpect(status().isAccepted());
        await().during(Duration.ofMillis(500)).until(() -> correosPara(correo).size() == 1);

        envejecerTokens(correo);
        reenviar(correo).andExpect(status().isAccepted());
        await().until(() -> correosPara(correo).size() == 2);
        String segundo = tokenDelEnlace(ultimoCorreoPara(correo), "verificar-correo");

        verificar(primero).andExpect(status().isBadRequest());
        verificar(segundo).andExpect(status().isNoContent());
    }

    @Test
    void reenviarAUnCorreoInexistenteOYaVerificadoRespondeIgualSinEnviarNada() throws Exception {
        String inexistente = UUID.randomUUID() + "@prueba.co";
        String verificado = registrar();
        verificar(tokenDelEnlace(ultimoCorreoPara(verificado), "verificar-correo")).andExpect(status().isNoContent());
        envejecerTokens(verificado);

        reenviar(inexistente).andExpect(status().isAccepted());
        reenviar(verificado).andExpect(status().isAccepted());

        await().during(Duration.ofMillis(500)).until(() -> correosPara(inexistente).isEmpty() && correosPara(verificado).size() == 1);
    }

    @Test
    void lasCuentasCreadasFueraDelRegistroYaEstanVerificadas() {
        UUID usuario = crearUsuario(Rol.HUESPED);

        assertThat(comoAplicacion.queryForObject("SELECT correo_verificado FROM usuario WHERE id = ?", Boolean.class, usuario)).isTrue();
    }

    private String registrar() throws Exception {
        String correo = UUID.randomUUID() + "@prueba.co";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Laura","apellido":"Gómez","correo":"%s","telefono":"3001234567","contrasena":"%s","rol":"HUESPED"}"""
                .formatted(correo, CONTRASENA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correoVerificado").value(false));
        return correo;
    }

    private void envejecerTokens(String correo) {
        comoAplicacion.update("""
                UPDATE token_correo SET creado_en = creado_en - interval '2 minutes'
                WHERE usuario_id = (SELECT id FROM usuario WHERE correo = ?)""", correo);
    }

    private ResultActions verificar(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)));
    }

    private ResultActions reenviar(String correo) throws Exception {
        return mvc.perform(post("/api/v1/auth/email/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\"}".formatted(correo)));
    }

    private ResultActions iniciarSesion(String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)));
    }
}
