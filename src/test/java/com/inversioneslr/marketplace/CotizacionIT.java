package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Publicación de 100.000 por noche, con capacidad 4 y disponible en 2030. Servicio 10 %, promoción 5 % y 10 % desde 7 noches. */
class CotizacionIT extends PruebaIntegracion {

    private UUID publicacion;
    private UUID desayuno;
    private String huesped;

    @BeforeEach
    void preparar() {
        publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        desayuno = comoAplicacion.queryForObject("INSERT INTO servicio_adicional (publicacion_id, nombre, costo) VALUES (?, 'Desayuno', 120000) RETURNING id",
                UUID.class, publicacion);
        huesped = sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED);
    }

    @Test
    void desglosaComoElMockupSinCrearReserva() throws Exception {
        String respuesta = cotizar("2030-03-10", "2030-03-13", 2, desayuno)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noches").value(3))
                .andExpect(jsonPath("$.porcentajeServicio").value(10.0))
                .andExpect(jsonPath("$.descuentos.length()").value(1))
                .andExpect(jsonPath("$.descuentos[0].concepto").value("Descuento promocional (5 %)"))
                .andExpect(jsonPath("$.adicionales[0].concepto").value("Desayuno"))
                .andExpect(jsonPath("$.moneda").value("COP"))
                .andExpect(jsonPath("$.adicionalesDisponibles[0].id").value(desayuno.toString()))
                .andExpect(jsonPath("$.adicionalesDisponibles[0].nombre").value("Desayuno"))
                .andReturn().getResponse().getContentAsString();

        assertThat(monto(respuesta, "$.precioNoche")).isEqualByComparingTo("100000");
        assertThat(monto(respuesta, "$.subtotal")).isEqualByComparingTo("300000");
        assertThat(monto(respuesta, "$.tarifaLimpieza")).isEqualByComparingTo("0");
        assertThat(monto(respuesta, "$.tarifaServicio")).isEqualByComparingTo("30000");
        assertThat(monto(respuesta, "$.descuentos[0].monto")).isEqualByComparingTo("15000");
        assertThat(monto(respuesta, "$.adicionales[0].monto")).isEqualByComparingTo("120000");
        assertThat(monto(respuesta, "$.total")).isEqualByComparingTo("435000");
        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM reserva WHERE publicacion_id = ?", Long.class, publicacion)).isZero();
    }

    @Test
    void desdeSieteNochesSumaElDescuentoPorEstanciaLarga() throws Exception {
        String respuesta = cotizar("2030-03-10", "2030-03-17", 2).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(respuesta, "$.descuentos[1].concepto")).isEqualTo("Descuento por estancia larga (10 %)");
        assertThat(monto(respuesta, "$.descuentos[1].monto")).isEqualByComparingTo("70000");
        assertThat(monto(respuesta, "$.total")).isEqualByComparingTo("665000");
    }

    @Test
    void usaLosPorcentajesVigentesDeLaConfiguracion() throws Exception {
        comoAplicacion.update("UPDATE configuracion_tarifa SET porcentaje = 12 WHERE clave = 'SERVICIO'");
        try {
            String respuesta = cotizar("2030-03-10", "2030-03-13", 2).andReturn().getResponse().getContentAsString();

            assertThat(monto(respuesta, "$.tarifaServicio")).isEqualByComparingTo("36000");
        } finally {
            comoAplicacion.update("UPDATE configuracion_tarifa SET porcentaje = 10 WHERE clave = 'SERVICIO'");
        }
    }

    @Test
    void laReservaCobraLoMismoQueSeCotizo() throws Exception {
        String cotizacion = cotizar("2030-04-10", "2030-04-13", 2, desayuno).andReturn().getResponse().getContentAsString();

        String reserva = mvc.perform(post("/api/v1/reservas").header("Authorization", huesped).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("2030-04-10", "2030-04-13", 2, desayuno)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(monto(reserva, "$.total")).isEqualByComparingTo(monto(cotizacion, "$.total"));
        assertThat(comoAplicacion.queryForObject("SELECT tarifa_servicio FROM reserva WHERE id = ?::uuid", BigDecimal.class,
                JsonPath.<String>read(reserva, "$.id"))).isEqualByComparingTo("30000");
    }

    @Test
    void datosInvalidosSeRechazanIndicandoElCampo() throws Exception {
        cotizar("2030-03-13", "2030-03-10", 2).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.salida").exists());
        cotizar("2030-03-10", "2030-03-13", 5).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.huespedes").exists());
        cotizar("2030-03-10", "2030-03-13", 2, UUID.randomUUID()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.adicionales").exists());
    }

    @Test
    void fechasNoDisponiblesOAlojamientoInactivoResponden409() throws Exception {
        cotizar("2031-03-10", "2031-03-13", 2).andExpect(status().isConflict());
        comoAplicacion.update("UPDATE publicacion SET estado = 'PAUSADA' WHERE id = ?", publicacion);
        cotizar("2030-03-10", "2030-03-13", 2).andExpect(status().isConflict());
    }

    @Test
    void soloUnHuespedCotiza() throws Exception {
        mvc.perform(post("/api/v1/cotizaciones").header("Authorization", sesionDe(crearUsuario(Rol.ANFITRION), Rol.ANFITRION))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("2030-03-10", "2030-03-13", 2)))
                .andExpect(status().isForbidden());
    }

    private ResultActions cotizar(String llegada, String salida, int huespedes, UUID... adicionales) throws Exception {
        return mvc.perform(post("/api/v1/cotizaciones").header("Authorization", huesped)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(llegada, salida, huespedes, adicionales)));
    }

    private String cuerpo(String llegada, String salida, int huespedes, UUID... adicionales) {
        String ids = adicionales.length == 0 ? "" : "\"" + String.join("\",\"", Arrays.stream(adicionales).map(UUID::toString).toList()) + "\"";
        return """
                {"publicacionId":"%s","llegada":"%s","salida":"%s","huespedes":%d,"adicionales":[%s]}"""
                .formatted(publicacion, llegada, salida, huespedes, ids);
    }

    private static BigDecimal monto(String json, String ruta) {
        return new BigDecimal(JsonPath.read(json, ruta).toString());
    }
}
