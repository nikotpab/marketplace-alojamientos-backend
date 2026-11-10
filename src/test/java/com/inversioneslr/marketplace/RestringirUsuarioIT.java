package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** CU-25 · RF-28. */
class RestringirUsuarioIT extends PruebaIntegracion {

    private static final String CONTRASENA = "Contrasena-de-prueba-1";

    @Test
    void unUsuarioRestringidoNoPuedeIniciarSesionYLaRestriccionSeAudita() throws Exception {
        String correo = registrar();
        UUID usuario = comoAplicacion.queryForObject("SELECT id FROM usuario WHERE correo = ?", UUID.class, correo);
        UUID admin = crearUsuario(Rol.ADMINISTRADOR);

        restringir(sesionDe(admin, Rol.ADMINISTRADOR), usuario, "Publica contenido falso").andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, CONTRASENA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_RESTRINGIDA"));
        assertThat(comoAplicacion.queryForObject("SELECT motivo_restriccion FROM usuario WHERE id = ?", String.class, usuario))
                .isEqualTo("Publica contenido falso");
        assertThat(comoAplicacion.queryForObject("""
                SELECT detalle ->> 'motivo' FROM auditoria.evento_auditoria
                WHERE actor_id = ? AND entidad_id = ? AND accion = 'UsuarioRestringido'""", String.class, admin, usuario))
                .isEqualTo("Publica contenido falso");
    }

    @Test
    void elAdministradorVeLaListaDeUsuariosConSuEstado() throws Exception {
        UUID usuario = crearUsuario(Rol.ANFITRION);
        String sesion = sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR);
        restringir(sesion, usuario, "Uso indebido").andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/admin/usuarios").header("Authorization", sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].restringido".formatted(usuario)).value(true))
                .andExpect(jsonPath("$[?(@.id == '%s')].rol".formatted(usuario)).value("ANFITRION"));
    }

    @Test
    void elMotivoEsObligatorio() throws Exception {
        restringir(sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR), crearUsuario(Rol.HUESPED), " ")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unAdministradorNoSeRestringe() throws Exception {
        restringir(sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR), crearUsuario(Rol.ADMINISTRADOR), "Prueba")
                .andExpect(status().isConflict());
    }

    @Test
    void soloElAdministradorRestringe() throws Exception {
        restringir(sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED), crearUsuario(Rol.HUESPED), "Me cae mal")
                .andExpect(status().isForbidden());
    }

    private ResultActions restringir(String sesion, UUID usuario, String motivo) throws Exception {
        return mvc.perform(post("/api/v1/admin/usuarios/{id}/restriccion", usuario)
                .header("Authorization", sesion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + motivo + "\"}"));
    }

    private String registrar() throws Exception {
        String correo = UUID.randomUUID() + "@prueba.co";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Laura","apellido":"Gómez","correo":"%s","telefono":"3001234567","contrasena":"%s","rol":"HUESPED"}"""
                .formatted(correo, CONTRASENA))).andExpect(status().isCreated());
        return correo;
    }
}
