package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class RegistroUsuarioIT extends PruebaIntegracion {

    private static final String CONTRASENA = "Contrasena-de-prueba-1";

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

        registrar(correo.toUpperCase(), "HUESPED", CONTRASENA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.correo").exists());
    }

    @Test
    void elRolAdministradorNoSePuedeElegir() throws Exception {
        String correo = correoNuevo();

        registrar(correo, "ADMINISTRADOR", CONTRASENA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.rol").exists());

        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM usuario WHERE correo = ?", Long.class, correo)).isZero();
    }

    @Test
    void losDatosInvalidosSeRechazanIndicandoElCampo() throws Exception {
        registrar("no-es-correo", "HUESPED", "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.correo").exists())
                .andExpect(jsonPath("$.campos.contrasena").exists());
    }

    private ResultActions registrar(String correo, String rol, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Laura","apellido":"Gómez","correo":"%s","telefono":"3001234567","contrasena":"%s","rol":"%s"}"""
                        .formatted(correo, contrasena, rol)));
    }

    private String hashDe(String correo) {
        return comoAplicacion.queryForObject("SELECT hash_contrasena FROM usuario WHERE correo = ?", String.class, correo);
    }

    private static String correoNuevo() {
        return UUID.randomUUID() + "@prueba.co";
    }
}
