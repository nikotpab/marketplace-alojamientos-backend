package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class RegistroUsuarioIT extends PruebaIntegracion {

    private static final String CONTRASENA = "Contrasena-de-prueba-1";
    /** Para los casos que se rechazan antes de comprobar el código. */
    private static final String CODIGO_CUALQUIERA = "000000";

    @Autowired
    PasswordEncoder cifrador;

    @Test
    void registraAlUsuarioGuardandoSoloElHashDeLaContrasena() throws Exception {
        String correo = correoNuevo();

        registrar(correo, "HUESPED", CONTRASENA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value(correo))
                .andExpect(jsonPath("$.rol").value("HUESPED"))
                .andExpect(jsonPath("$.cuentaActiva").value(true))
                .andExpect(jsonPath("$.correoVerificado").value(true))
                .andExpect(jsonPath("$.hashContrasena").doesNotExist());

        String hash = comoAplicacion.queryForObject("SELECT hash_contrasena FROM usuario WHERE correo = ?", String.class, correo);
        assertThat(hash).isNotEqualTo(CONTRASENA);
        assertThat(cifrador.matches(CONTRASENA, hash)).isTrue();
    }

    @Test
    void dosUsuariosConLaMismaContrasenaTienenHashDistinto() throws Exception {
        String primero = correoNuevo();
        String segundo = correoNuevo();
        registrar(primero, "HUESPED", CONTRASENA).andExpect(status().isCreated());
        registrar(segundo, "ANFITRION", CONTRASENA).andExpect(status().isCreated());

        assertThat(hashDe(primero)).isNotEqualTo(hashDe(segundo));
    }

    @Test
    void elRegistroQuedaEnAuditoria() throws Exception {
        String correo = correoNuevo();

        registrar(correo, "ANFITRION", CONTRASENA).andExpect(status().isCreated());

        UUID usuario = comoAplicacion.queryForObject("SELECT id FROM usuario WHERE correo = ?", UUID.class, correo);
        assertThat(comoAplicacion.queryForObject(
                "SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'UsuarioRegistrado'", Long.class, usuario)).isEqualTo(1);
    }

    @Test
    void unCorreoRepetidoSeRechazaJuntoAlCampoAunqueCambienLasMayusculas() throws Exception {
        String correo = correoNuevo();
        registrar(correo, "HUESPED", CONTRASENA).andExpect(status().isCreated());

        // El correo repetido se rechaza antes de mirar el código.
        registrar(correo.toUpperCase(), "HUESPED", CONTRASENA, CODIGO_CUALQUIERA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.correo").exists());
    }

    @Test
    void elRolAdministradorNoSePuedeElegir() throws Exception {
        String correo = correoNuevo();

        registrar(correo, "ADMINISTRADOR", CONTRASENA, CODIGO_CUALQUIERA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.rol").exists());

        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM usuario WHERE correo = ?", Long.class, correo)).isZero();
    }

    @Test
    void losDatosInvalidosSeRechazanIndicandoElCampo() throws Exception {
        registrar("no-es-correo", "HUESPED", "corta", CODIGO_CUALQUIERA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.correo").exists())
                .andExpect(jsonPath("$.campos.contrasena").exists());
    }

    /** Con el código que llegó al correo, como en el registro real (SCRUM-151). */
    private ResultActions registrar(String correo, String rol, String contrasena) throws Exception {
        return registrar(correo, rol, contrasena, codigoDeRegistro(correo));
    }

    private ResultActions registrar(String correo, String rol, String contrasena, String codigo) throws Exception {
        return registrar(correo, "3001234567", rol, contrasena, codigo);
    }

    private ResultActions registrar(String correo, String telefono, String rol, String contrasena, String codigo) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Laura","apellido":"Gómez","correo":"%s","telefono":"%s","contrasena":"%s","rol":"%s","codigo":"%s"}"""
                        .formatted(correo, telefono, contrasena, rol, codigo)));
    }

    /** SCRUM-154: celular de Colombia. Se rechaza antes de mirar el código. */
    @Test
    void elTelefonoDebeTenerDiezDigitosYEmpezarPorTres() throws Exception {
        for (String telefono : List.of("2001234567", "300123456", "30012345678", "300123456a", "")) {
            registrar(correoNuevo(), telefono, "HUESPED", CONTRASENA, CODIGO_CUALQUIERA)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.telefono").value("El teléfono debe tener 10 dígitos y empezar por 3."));
        }
    }

    /** SCRUM-154: @Email solo aceptaba "luicuadros07@gmail". */
    @Test
    void elCorreoNecesitaUnDominioCompleto() throws Exception {
        for (String correo : List.of("luicuadros07@gmail", "ana@gmail.", "ana@gmail.c", "ana@.com", "ana gomez@gmail.com")) {
            registrar(correo, "HUESPED", CONTRASENA, CODIGO_CUALQUIERA)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.correo").value(containsString("nombre@gmail.com")));
        }
    }

    private String hashDe(String correo) {
        return comoAplicacion.queryForObject("SELECT hash_contrasena FROM usuario WHERE correo = ?", String.class, correo);
    }

    private static String correoNuevo() {
        return UUID.randomUUID() + "@prueba.co";
    }
}
