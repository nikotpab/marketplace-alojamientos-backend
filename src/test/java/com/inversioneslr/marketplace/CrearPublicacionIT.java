package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class CrearPublicacionIT extends PruebaIntegracion {

    static final String COMPLETA = """
            {"titulo":"Casa con patio","descripcion":"Casa amplia","ubicacion":"Centro histórico, calle de la Factoría, Cartagena",
             "tipo":"CASA","capacidad":4,"precioNoche":280000,"servicios":["WiFi","Cocina"],
             "reglas":["Llegada desde las 3:00 p. m.","No se permiten fiestas"],"imagenes":["https://ejemplo.co/1.jpg"],
             "politicaCancelacion":"Flexible: 100 % hasta 5 días antes"}""";

    private UUID anfitrion;

    @BeforeEach
    void preparar() {
        anfitrion = crearUsuario(Rol.ANFITRION);
    }

    @Test
    void creaElBorradorConElFormatoDelFrontend() throws Exception {
        crear(COMPLETA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.actualizadaEn").exists())
                .andExpect(jsonPath("$.titulo").value("Casa con patio"))
                .andExpect(jsonPath("$.ubicacion").value("Centro histórico, calle de la Factoría, Cartagena"))
                .andExpect(jsonPath("$.tipo").value("CASA"))
                .andExpect(jsonPath("$.capacidad").value(4))
                .andExpect(jsonPath("$.servicios[0]").value("Cocina"))
                .andExpect(jsonPath("$.reglas[0]").value("Llegada desde las 3:00 p. m."))
                .andExpect(jsonPath("$.reglas[1]").value("No se permiten fiestas"))
                .andExpect(jsonPath("$.imagenes[0]").value("https://ejemplo.co/1.jpg"))
                .andExpect(jsonPath("$.politicaCancelacion").value("Flexible"))
                .andExpect(jsonPath("$.anfitrion").value("Nombre Apellido"));
    }

    @Test
    void laCiudadSaleDeLoQueVaDespuesDeLaUltimaComaDeLaUbicacion() throws Exception {
        UUID id = idDe(crear(COMPLETA));

        assertThat(comoAplicacion.queryForObject("SELECT ciudad FROM publicacion WHERE id = ?", String.class, id)).isEqualTo("Cartagena");
    }

    @Test
    void unBorradorPuedeGuardarseIncompleto() throws Exception {
        crear("{\"titulo\":\"Solo el título\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.tipo").doesNotExist())
                .andExpect(jsonPath("$.politicaCancelacion").doesNotExist());
    }

    @Test
    void unaPoliticaSinNombreConocidoQuedaComoFlexible() throws Exception {
        crear(COMPLETA.replace("Flexible: 100 % hasta 5 días antes", "Devolución parcial hasta 2 días antes"))
                .andExpect(jsonPath("$.politicaCancelacion").value("Flexible"));
    }

    @Test
    void quedaDisponibleDesdeHoyDuranteDosAnos() throws Exception {
        UUID id = idDe(crear(COMPLETA));
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        var periodo = comoAplicacion.queryForMap("SELECT fecha_inicio, fecha_fin, tipo FROM periodo_disponibilidad WHERE publicacion_id = ?", id);
        assertThat(periodo).containsEntry("tipo", "DISPONIBLE");
        assertThat(periodo.get("fecha_inicio")).hasToString(hoy.toString());
        assertThat(periodo.get("fecha_fin")).hasToString(hoy.plusYears(2).toString());
    }

    @Test
    void unTipoOServicioInexistenteSeRechazaIndicandoElCampo() throws Exception {
        crear(COMPLETA.replace("\"CASA\"", "\"CASTILLO\"")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.tipo").exists());
        crear(COMPLETA.replace("\"Cocina\"", "\"Helipuerto\"")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.servicios").exists());
    }

    @Test
    void capacidadYPrecioDebenSerPositivosSiSeIndican() throws Exception {
        crear("{\"titulo\":\"Casa\",\"capacidad\":0,\"precioNoche\":-5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.capacidad").exists())
                .andExpect(jsonPath("$.campos.precioNoche").exists());
    }

    @Test
    void soloUnAnfitrionPuedeCrear() throws Exception {
        mvc.perform(post("/api/v1/publicaciones").header("Authorization", sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED))
                        .contentType(MediaType.APPLICATION_JSON).content(COMPLETA))
                .andExpect(status().isForbidden());
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/publicaciones").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static UUID idDe(ResultActions respuesta) throws Exception {
        return UUID.fromString(JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id"));
    }
}
