package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** CU-28 · RF-32. */
class TarifasIT extends PruebaIntegracion {

    private static final String NUEVAS = """
            {"porcentajeLimpieza":0,"porcentajeServicio":12,"porcentajeDescuento":5,
             "porcentajeDescuentoEstanciaLarga":10,"nochesEstanciaLarga":7}""";

    /** Vuelve a los valores de V3, que usan las demás pruebas. */
    @AfterEach
    void restaurar() {
        comoAplicacion.update("""
                UPDATE configuracion_tarifa SET porcentaje = CASE clave
                    WHEN 'LIMPIEZA' THEN NULL WHEN 'SERVICIO' THEN 10 WHEN 'DESCUENTO_PROMOCIONAL' THEN 5 ELSE 10 END,
                    noches_minimas = CASE clave WHEN 'DESCUENTO_ESTANCIA_LARGA' THEN 7 END,
                    modificado_por = NULL""");
    }

    @Test
    void elAdministradorCambiaLasTarifasYAplicanALaSiguienteCotizacion() throws Exception {
        UUID administrador = crearUsuario(Rol.ADMINISTRADOR);
        String sesion = sesionDe(administrador, Rol.ADMINISTRADOR);

        mvc.perform(put("/api/v1/admin/tarifas").header("Authorization", sesion).contentType(MediaType.APPLICATION_JSON).content(NUEVAS))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/admin/tarifas").header("Authorization", sesion))
                .andExpect(jsonPath("$.porcentajeServicio").value(12.0));
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        mvc.perform(post("/api/v1/cotizaciones").header("Authorization", sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publicacionId":"%s","llegada":"2030-06-10","salida":"2030-06-13","huespedes":2}""".formatted(publicacion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porcentajeServicio").value(12.0));
        assertThat(comoAplicacion.queryForObject(
                "SELECT modificado_por FROM configuracion_tarifa WHERE clave = 'SERVICIO'", UUID.class)).isEqualTo(administrador);
    }

    @Test
    void elCambioQuedaEnLaAuditoriaConElValorAnteriorYElNuevo() throws Exception {
        UUID administrador = crearUsuario(Rol.ADMINISTRADOR);

        mvc.perform(put("/api/v1/admin/tarifas").header("Authorization", sesionDe(administrador, Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON).content(NUEVAS))
                .andExpect(status().isNoContent());

        assertThat(comoAplicacion.queryForObject("""
                SELECT detalle ->> 'Tarifa de servicio' FROM auditoria.evento_auditoria
                WHERE actor_id = ? AND accion = 'TarifasActualizadas'""", String.class, administrador)).isEqualTo("10 % → 12 %");
    }

    @Test
    void unPorcentajeFueraDeRangoSeRechaza() throws Exception {
        mvc.perform(put("/api/v1/admin/tarifas").header("Authorization", sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON).content(NUEVAS.replace("\"porcentajeServicio\":12", "\"porcentajeServicio\":120")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloElAdministradorCambiaLasTarifas() throws Exception {
        mvc.perform(put("/api/v1/admin/tarifas").header("Authorization", sesionDe(crearUsuario(Rol.ANFITRION), Rol.ANFITRION))
                        .contentType(MediaType.APPLICATION_JSON).content(NUEVAS))
                .andExpect(status().isForbidden());
    }
}
