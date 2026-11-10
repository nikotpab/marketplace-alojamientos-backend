package com.inversioneslr.marketplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** SCRUM-99: la documentación de la API se publica sin sesión y solo cubre /api/v1. */
class DocumentacionApiIT extends PruebaIntegracion {

    @Test
    void laEspecificacionOpenApiSePublicaSinSesion() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Marketplace de alojamientos - API"))
                .andExpect(jsonPath("$.paths['/api/v1/reservas'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.jwt.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
    }

    @Test
    void swaggerUiSeSirveSinSesion() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
