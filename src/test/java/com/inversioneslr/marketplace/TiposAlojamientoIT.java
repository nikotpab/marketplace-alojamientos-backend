package com.inversioneslr.marketplace;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class TiposAlojamientoIT extends PruebaIntegracion {

    @Test
    void listaLosTiposActivosConSuCodigo() throws Exception {
        mvc.perform(get("/api/v1/configuracion/tipos-alojamiento").param("activos", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'CABANA')].nombre").value(hasItem("Cabaña")))
                .andExpect(jsonPath("$[*].codigo").value(not(hasItem("GLAMPING"))));
    }
}
