package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SolicitarActivacionIT extends PruebaIntegracion {

    private UUID anfitrion;

    @BeforeEach
    void preparar() {
        anfitrion = crearUsuario(Rol.ANFITRION);
    }

    @Test
    void unBorradorCompletoPasaARevisionYQuedaEnAuditoria() throws Exception {
        UUID borrador = crearBorrador(CrearPublicacionIT.COMPLETA);

        solicitar(borrador, anfitrion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_REVISION"));

        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'PublicacionEnviadaARevision'",
                Long.class, borrador)).isEqualTo(1);
    }

    @Test
    void siFaltaInformacionResponde422ConElMensajeDeCadaCampoYSigueEnBorrador() throws Exception {
        UUID borrador = crearBorrador("{\"titulo\":\"Solo el título\",\"capacidad\":2}");

        solicitar(borrador, anfitrion)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errores.descripcion").value("Agrega una descripción del alojamiento."))
                .andExpect(jsonPath("$.errores.ubicacion").value("Indica la ubicación del alojamiento."))
                .andExpect(jsonPath("$.errores.tipo").value("Selecciona el tipo de alojamiento."))
                .andExpect(jsonPath("$.errores.precioNoche").value("Indica un precio por noche mayor que 0."))
                .andExpect(jsonPath("$.errores.imagenes").value("Agrega al menos una referencia de imagen (URL)."))
                .andExpect(jsonPath("$.errores.politicaCancelacion").value("Describe la política de cancelación."))
                .andExpect(jsonPath("$.errores.titulo").doesNotExist())
                .andExpect(jsonPath("$.errores.capacidad").doesNotExist());

        assertThat(estadoDe(borrador)).isEqualTo("BORRADOR");
    }

    @Test
    void alReenviarUnBorradorRechazadoSeBorraElMotivo() throws Exception {
        UUID borrador = crearBorrador(CrearPublicacionIT.COMPLETA);
        comoAplicacion.update("UPDATE publicacion SET motivo_estado = 'Faltan fotos' WHERE id = ?", borrador);

        solicitar(borrador, anfitrion).andExpect(jsonPath("$.motivoRechazo").doesNotExist());

        assertThat(comoAplicacion.queryForObject("SELECT motivo_estado FROM publicacion WHERE id = ?", String.class, borrador)).isNull();
    }

    @Test
    void soloUnBorradorPuedeEnviarseARevision() throws Exception {
        solicitar(crearPublicacionActiva(anfitrion), anfitrion).andExpect(status().isConflict());
    }

    @Test
    void otroAnfitrionNoPuedeSolicitarla() throws Exception {
        UUID borrador = crearBorrador(CrearPublicacionIT.COMPLETA);

        solicitar(borrador, crearUsuario(Rol.ANFITRION)).andExpect(status().isForbidden());

        assertThat(estadoDe(borrador)).isEqualTo("BORRADOR");
    }

    private UUID crearBorrador(String cuerpo) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(respuesta, "$.id"));
    }

    private ResultActions solicitar(UUID publicacion, UUID usuario) throws Exception {
        return mvc.perform(post("/api/v1/publicaciones/{id}/solicitar-activacion", publicacion)
                .header("Authorization", sesionDe(usuario, Rol.ANFITRION)));
    }

    private String estadoDe(UUID publicacion) {
        return comoAplicacion.queryForObject("SELECT estado FROM publicacion WHERE id = ?", String.class, publicacion);
    }
}
