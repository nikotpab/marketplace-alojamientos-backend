package com.inversioneslr.marketplace;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/** Verifica que el script del perfil demo cargue y sea visible en la búsqueda y el detalle. */
@ActiveProfiles("demo")
class DatosDemostracionIT extends PruebaIntegracion {

    private static final String CASA_COLONIAL = "a0000000-0000-0000-0000-000000000001";

    @Test
    void laBusquedaMuestraLasPublicacionesDeDemostracion() throws Exception {
        mvc.perform(get("/api/v1/alojamientos").param("ciudad", "Salento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].titulo").value(hasItem("Cabaña cafetera")));
        mvc.perform(get("/api/v1/alojamientos").param("tipo", "HABITACION").param("ciudad", "bogota"))
                .andExpect(jsonPath("$.contenido[*].titulo").value(hasItem("Habitación en La Candelaria")));
    }

    @Test
    void elDetalleMuestraResenasYCalificacion() throws Exception {
        mvc.perform(get("/api/v1/alojamientos/{id}", CASA_COLONIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Casa colonial con patio"))
                .andExpect(jsonPath("$.calificacionPromedio").value(4.5))
                .andExpect(jsonPath("$.resenas.length()").value(2))
                .andExpect(jsonPath("$.imagenes.length()").value(3));
    }
}
