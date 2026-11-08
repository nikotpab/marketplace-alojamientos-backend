package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class EditarPublicacionIT extends PruebaIntegracion {

    private UUID anfitrion;
    private UUID publicacion;
    private String cuerpo;

    @BeforeEach
    void preparar() {
        anfitrion = crearUsuario(Rol.ANFITRION);
        publicacion = crearPublicacionActiva(anfitrion);
        cuerpo = cuerpo(6, "250000");
    }

    @Test
    void elPropietarioEditaSinCambiarElEstadoYQuedaEnAuditoria() throws Exception {
        editar(sesionDe(anfitrion, Rol.ANFITRION), publicacion, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Título nuevo"))
                .andExpect(jsonPath("$.ubicacion").value("El Poblado, Medellín"))
                .andExpect(jsonPath("$.servicios[0]").value("WiFi"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        assertThat(comoAplicacion.queryForObject("SELECT capacidad FROM publicacion WHERE id = ?", Integer.class, publicacion)).isEqualTo(6);
        assertThat(comoAplicacion.queryForObject("SELECT ciudad FROM publicacion WHERE id = ?", String.class, publicacion)).isEqualTo("Medellín");
        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'PublicacionEditada' AND actor_id = ?", publicacion, anfitrion)).isEqualTo(1);
    }

    @Test
    void unAdministradorPuedeEditarUnaPublicacionAjena() throws Exception {
        editar(sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR), publicacion, cuerpo).andExpect(status().isOk());

        assertThat(comoAplicacion.queryForObject("SELECT anfitrion_id FROM publicacion WHERE id = ?", UUID.class, publicacion)).isEqualTo(anfitrion);
    }

    @Test
    void otroAnfitrionRecibe403ElIntentoQuedaRegistradoYNoCambiaNada() throws Exception {
        UUID otro = crearUsuario(Rol.ANFITRION);

        editar(sesionDe(otro, Rol.ANFITRION), publicacion, cuerpo).andExpect(status().isForbidden());

        assertThat(comoAplicacion.queryForObject("SELECT titulo FROM publicacion WHERE id = ?", String.class, publicacion)).isEqualTo("Casa de prueba");
        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE actor_id = ? AND accion = 'AccesoDenegado'", otro)).isEqualTo(1);
    }

    @Test
    void unHuespedNoPuedeEditar() throws Exception {
        editar(sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED), publicacion, cuerpo).andExpect(status().isForbidden());
    }

    @Test
    void publicacionInexistenteResponde404() throws Exception {
        editar(sesionDe(anfitrion, Rol.ANFITRION), UUID.randomUUID(), cuerpo).andExpect(status().isNotFound());
    }

    @Test
    void capacidadYPrecioInvalidosSeRechazanIndicandoElCampo() throws Exception {
        editar(sesionDe(anfitrion, Rol.ANFITRION), publicacion, cuerpo(0, "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.capacidad").value("La capacidad debe ser mayor que cero. Ingresa 1 o más."))
                .andExpect(jsonPath("$.campos.precioNoche").exists())
                .andExpect(jsonPath("$.errores.capacidad").value("La capacidad debe ser mayor que cero. Ingresa 1 o más."));
    }

    @Test
    void unaPublicacionQueNoEsBorradorNoPuedeQuedarIncompleta() throws Exception {
        editar(sesionDe(anfitrion, Rol.ANFITRION), publicacion, cuerpo.replace("\"Título nuevo\"", "\"\"").replace("[\"a.jpg\"]", "[]"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("INFORMACION_INCOMPLETA"))
                .andExpect(jsonPath("$.errores.titulo").value("Agrega un título para la publicación."))
                .andExpect(jsonPath("$.errores.imagenes").value("Agrega al menos una referencia de imagen (URL)."))
                .andExpect(jsonPath("$.errores.descripcion").doesNotExist());

        assertThat(comoAplicacion.queryForObject("SELECT titulo FROM publicacion WHERE id = ?", String.class, publicacion)).isEqualTo("Casa de prueba");
    }

    @Test
    void unTipoDeAlojamientoInexistenteSeRechaza() throws Exception {
        editar(sesionDe(anfitrion, Rol.ANFITRION), publicacion, cuerpo.replace("\"CASA\"", "\"CASTILLO\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.tipo").exists());
    }

    private ResultActions editar(String sesion, UUID id, String contenido) throws Exception {
        return mvc.perform(put("/api/v1/publicaciones/{id}", id)
                .header("Authorization", sesion)
                .contentType(MediaType.APPLICATION_JSON)
                .content(contenido));
    }

    /** Con el formato que envía el formulario del frontend. */
    private static String cuerpo(int capacidad, String precio) {
        return """
                {"titulo":"Título nuevo","descripcion":"Descripción","ubicacion":"El Poblado, Medellín","tipo":"CASA",
                 "capacidad":%d,"precioNoche":%s,"servicios":["WiFi"],"reglas":["No se permiten fiestas"],
                 "imagenes":["a.jpg"],"politicaCancelacion":"Flexible"}""".formatted(capacidad, precio);
    }

    private long contar(String sql, Object... valores) {
        return comoAplicacion.queryForObject(sql, Long.class, valores);
    }
}
