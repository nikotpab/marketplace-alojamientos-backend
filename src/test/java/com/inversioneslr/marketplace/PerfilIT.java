package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PerfilIT extends PruebaIntegracion {

    private UUID usuario;
    private String sesion;

    @BeforeEach
    void preparar() {
        usuario = crearUsuario(Rol.ANFITRION);
        sesion = sesionDe(usuario, Rol.ANFITRION);
    }

    @Test
    void consultaElPerfilDelUsuarioDeLaSesion() throws Exception {
        mvc.perform(get("/api/v1/usuarios/me").header("Authorization", sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre"))
                .andExpect(jsonPath("$.apellido").value("Apellido"))
                .andExpect(jsonPath("$.telefono").value("3000000000"))
                .andExpect(jsonPath("$.rol").value("ANFITRION"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.hashContrasena").doesNotExist());
    }

    @Test
    void actualizaNombreApellidoYTelefonoSinTocarElCorreo() throws Exception {
        String correo = comoAplicacion.queryForObject("SELECT correo FROM usuario WHERE id = ?", String.class, usuario);

        mvc.perform(put("/api/v1/usuarios/me").header("Authorization", sesion).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Martínez\",\"telefono\":\"3109876543\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.telefono").value("3109876543"))
                .andExpect(jsonPath("$.correo").value(correo));

        assertThat(comoAplicacion.queryForObject("SELECT apellido FROM usuario WHERE id = ?", String.class, usuario)).isEqualTo("Martínez");
    }

    @Test
    void losDatosInvalidosSeRechazanIndicandoElCampo() throws Exception {
        mvc.perform(put("/api/v1/usuarios/me").header("Authorization", sesion).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"apellido\":\"Martínez\",\"telefono\":\"3109876543\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists());
    }

    @Test
    void desactivarLaCuentaQuedaEnAuditoriaEImpideIniciarSesion() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/me/estado").header("Authorization", sesion).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        assertThat(comoAplicacion.queryForObject("SELECT cuenta_activa FROM usuario WHERE id = ?", Boolean.class, usuario)).isFalse();
        assertThat(comoAplicacion.queryForObject(
                "SELECT count(*) FROM auditoria.evento_auditoria WHERE actor_id = ? AND accion = 'CuentaDesactivada'", Long.class, usuario)).isEqualTo(1);
    }

    @Test
    void elEstadoEsObligatorio() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/me/estado").header("Authorization", sesion).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.activo").exists());
    }

    @Test
    void sinSesionNoSeConsultaElPerfil() throws Exception {
        mvc.perform(get("/api/v1/usuarios/me")).andExpect(status().isUnauthorized());
    }
}
