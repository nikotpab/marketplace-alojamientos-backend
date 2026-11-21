package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

/** El correo se verifica con un código antes de crear la cuenta. */
@ExtendWith(OutputCaptureExtension.class)
class CodigoRegistroIT extends PruebaIntegracion {

    private static final String CONTRASENA = "Contrasena-de-prueba-1";
    /** Al quinto intento fallido el código se anula. */
    private static final int INTENTOS_POR_CODIGO = 5;

    @Autowired
    JavaMailSenderImpl servidorDeCorreo;

    @Autowired
    PasswordEncoder cifrador;

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
    void elCodigoLlegaAlCorreoYCreaLaCuentaYaVerificada(CapturedOutput salida) throws Exception {
        String correo = correoNuevo();
        String codigo = codigoDeRegistro(correo);

        assertThat(ultimoCorreoPara(correo)).contains(codigo, "15 minutos").doesNotContain("http");
        String hash = comoAplicacion.queryForObject("SELECT hash_codigo FROM codigo_registro WHERE correo = ?", String.class, correo);
        assertThat(cifrador.matches(codigo, hash)).isTrue();

        registrar(correo, codigo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correoVerificado").value(true));
        iniciarSesion(correo).andExpect(status().isOk());

        // El código sirve una vez: se borra al usarlo, y no queda en los logs ni en la auditoría.
        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM codigo_registro WHERE correo = ?", Long.class, correo)).isZero();
        assertThat(comoAplicacion.queryForObject("""
                SELECT count(*) FROM auditoria.evento_auditoria
                WHERE (actor_id = ? OR entidad_id = ?) AND detalle::text LIKE ?""", Long.class,
                idDe(correo), idDe(correo), "%" + codigo + "%")).isZero();
        assertThat(salida.getOut()).doesNotContainPattern("(?<!\\d)" + codigo + "(?!\\d)");
    }

    @Test
    void conUnCodigoIncorrectoNoSeCreaLaCuentaYAlQuintoFalloSeAnula() throws Exception {
        String correo = correoNuevo();
        String codigo = codigoDeRegistro(correo);
        String otro = otroCodigo(codigo);

        for (int intento = 1; intento < INTENTOS_POR_CODIGO; intento++) {
            registrar(correo, otro)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.codigo").value(containsString("no es correcto")));
        }
        registrar(correo, otro)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.codigo").value(containsString("se anuló")));
        registrar(correo, codigo).andExpect(status().isBadRequest());

        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM usuario WHERE correo = ?", Long.class, correo)).isZero();
    }

    /** El intento se gasta antes de comparar: diez solicitudes a la vez no prueban más de cinco códigos. */
    @Test
    void niConIntentosSimultaneosSePruebanMasDeCincoCodigos() throws Exception {
        String correo = correoNuevo();
        String codigo = codigoDeRegistro(correo);
        String otro = otroCodigo(codigo);

        Callable<Integer> intento = () -> registrar(correo, otro).andReturn().getResponse().getStatus();
        try (ExecutorService hilos = Executors.newFixedThreadPool(10)) {
            for (Future<Integer> respuesta : hilos.invokeAll(List.of(intento, intento, intento, intento, intento,
                    intento, intento, intento, intento, intento))) {
                assertThat(respuesta.get()).isEqualTo(400);
            }
        }

        assertThat(comoAplicacion.queryForObject("SELECT intentos FROM codigo_registro WHERE correo = ?", Integer.class, correo))
                .isEqualTo(INTENTOS_POR_CODIGO);
        registrar(correo, codigo).andExpect(status().isBadRequest());
    }

    @Test
    void unCodigoVencidoNoSirve() throws Exception {
        String correo = correoNuevo();
        String codigo = codigoDeRegistro(correo);
        comoAplicacion.update("UPDATE codigo_registro SET expira_en = now() - interval '1 minute' WHERE correo = ?", correo);

        registrar(correo, codigo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.codigo").exists());
    }

    @Test
    void unCodigoNuevoSoloSaleUnMinutoDespuesYAnulaElAnterior() throws Exception {
        String correo = correoNuevo();
        String primero = codigoDeRegistro(correo);

        solicitarCodigo(correo).andExpect(status().isAccepted());
        await().during(Duration.ofMillis(500)).until(() -> correosPara(correo).size() == 1);

        comoAplicacion.update("UPDATE codigo_registro SET creado_en = creado_en - interval '2 minutes' WHERE correo = ?", correo);
        String segundo = codigoDeRegistro(correo);

        if (!segundo.equals(primero)) {
            registrar(correo, primero).andExpect(status().isBadRequest());
        }
        registrar(correo, segundo).andExpect(status().isCreated());
    }

    @Test
    void aUnCorreoYaRegistradoNoSeLeEnviaCodigo() throws Exception {
        String correo = comoAplicacion.queryForObject("SELECT correo FROM usuario WHERE id = ?", String.class, crearUsuario(Rol.HUESPED));

        solicitarCodigo(correo.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.correo").exists());
        await().during(Duration.ofMillis(500)).until(() -> correosPara(correo).isEmpty());
    }

    /** No se envía un código a un correo incompleto. */
    @Test
    void aUnCorreoIncompletoNoSeLeEnviaCodigo() throws Exception {
        String incompleto = "luicuadros07-" + UUID.randomUUID() + "@gmail";

        solicitarCodigo(incompleto)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.correo").value(containsString("nombre@gmail.com")));
        await().during(Duration.ofMillis(500)).until(() -> correosPara(incompleto).isEmpty());
    }

    @Test
    void sinCodigoOConOtroFormatoNoSeRegistra() throws Exception {
        String correo = correoNuevo();

        registrar(correo, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.codigo").exists());
        registrar(correo, "12ab56").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.codigo").exists());
        // Nunca se pidió un código para este correo.
        registrar(correo, "123456").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.codigo").exists());

        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM usuario WHERE correo = ?", Long.class, correo)).isZero();
    }

    /** Las cuentas creadas sin verificar el correo se verifican recuperando la contraseña. */
    @Test
    void unaCuentaAntiguaSinVerificarDebeRecuperarLaContrasena() throws Exception {
        String correo = correoNuevo();
        registrar(correo, codigoDeRegistro(correo)).andExpect(status().isCreated());
        comoAplicacion.update("UPDATE usuario SET correo_verificado = false WHERE correo = ?", correo);

        iniciarSesion(correo)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CORREO_NO_VERIFICADO"))
                .andExpect(jsonPath("$.detail").value(containsString("recupera tu contraseña")));
    }

    @Test
    void lasCuentasCreadasFueraDelRegistroYaEstanVerificadas() {
        UUID usuario = crearUsuario(Rol.HUESPED);

        assertThat(comoAplicacion.queryForObject("SELECT correo_verificado FROM usuario WHERE id = ?", Boolean.class, usuario)).isTrue();
    }

    private static String correoNuevo() {
        return UUID.randomUUID() + "@prueba.co";
    }

    private static String otroCodigo(String codigo) {
        return "%06d".formatted((Integer.parseInt(codigo) + 1) % 1_000_000);
    }

    private UUID idDe(String correo) {
        return comoAplicacion.queryForObject("SELECT id FROM usuario WHERE correo = ?", UUID.class, correo);
    }

    private ResultActions solicitarCodigo(String correo) throws Exception {
        return mvc.perform(post("/api/v1/auth/register/code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\"}".formatted(correo)));
    }

    private ResultActions registrar(String correo, String codigo) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Laura","apellido":"Gómez","correo":"%s","telefono":"3001234567","contrasena":"%s","rol":"HUESPED","codigo":"%s"}"""
                .formatted(correo, CONTRASENA, codigo)));
    }

    private ResultActions iniciarSesion(String correo) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, CONTRASENA)));
    }
}
