package com.inversioneslr.marketplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.usuarios.dominio.TokensCorreo;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** La siguiente petición con un token anterior responde 401. */
class SesionesIT extends PruebaIntegracion {

    @Autowired
    TokensCorreo tokensCorreo;

    private UUID huesped;
    private String sesion;

    @BeforeEach
    void preparar() throws Exception {
        huesped = crearUsuario(Rol.HUESPED);
        sesion = sesionDe(huesped, Rol.HUESPED);
        perfil(sesion).andExpect(status().isOk());
    }

    @Test
    void restringirAUnUsuarioInvalidaSuToken() throws Exception {
        mvc.perform(post("/api/v1/admin/usuarios/{id}/restriccion", huesped)
                        .header("Authorization", sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Fraude\"}"))
                .andExpect(status().isNoContent());

        perfil(sesion).andExpect(status().isUnauthorized());
    }

    @Test
    void desactivarLaCuentaInvalidaSuToken() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/me/estado").header("Authorization", sesion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());

        perfil(sesion).andExpect(status().isUnauthorized());
    }

    @Test
    void restablecerLaContrasenaInvalidaLosTokensAnterioresYElNuevoInicioDeSesionSirve() throws Exception {
        String enlace = tokensCorreo.emitir(huesped, TokensCorreo.Proposito.RESTABLECER_CONTRASENA, Instant.now().plus(Duration.ofHours(1)));
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"contrasenaNueva\":\"Nueva-contrasena-1\"}".formatted(enlace)))
                .andExpect(status().isNoContent());

        perfil(sesion).andExpect(status().isUnauthorized());

        String correo = comoAplicacion.queryForObject("SELECT correo FROM usuario WHERE id = ?", String.class, huesped);
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"Nueva-contrasena-1\"}".formatted(correo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        perfil("Bearer " + JsonPath.read(respuesta, "$.token")).andExpect(status().isOk());
    }

    @Test
    void losTokensDeOtrosUsuariosSiguenSirviendo() throws Exception {
        UUID otro = crearUsuario(Rol.HUESPED);
        String sesionOtro = sesionDe(otro, Rol.HUESPED);

        mvc.perform(patch("/api/v1/usuarios/me/estado").header("Authorization", sesion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());

        perfil(sesionOtro).andExpect(status().isOk());
    }

    private ResultActions perfil(String sesion) throws Exception {
        return mvc.perform(get("/api/v1/usuarios/me").header("Authorization", sesion));
    }
}
