package com.inversioneslr.marketplace;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * CU-29 · RF-33. Las reservas de la prueba se hicieron en marzo de 2001, un mes que no usa ninguna otra prueba, así que
 * los indicadores del periodo son exactos.
 */
class DashboardIT extends PruebaIntegracion {

    @Test
    void muestraLasReservasLosIngresosYLasCiudadesDelPeriodo() throws Exception {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID enMedellin = publicacionEn(anfitrion, "Medellín de prueba");
        UUID enSantaMarta = publicacionEn(anfitrion, "Santa Marta de prueba");
        UUID huesped = crearUsuario(Rol.HUESPED);
        reservar(huesped, enMedellin, "2030-01-01", "CONFIRMADA", 30000, "2001-03-05");
        reservar(huesped, enMedellin, "2030-02-01", "COMPLETADA", 20000, "2001-03-20");
        reservar(huesped, enSantaMarta, "2030-03-01", "CONFIRMADA", 10000, "2001-03-31");
        reservar(huesped, enSantaMarta, "2030-04-01", "CANCELADA", 50000, "2001-03-10");
        reservar(huesped, enSantaMarta, "2030-05-01", "CONFIRMADA", 70000, "2001-04-01");

        mvc.perform(get("/api/v1/admin/dashboard").param("desde", "2001-03-01").param("hasta", "2001-03-31")
                        .header("Authorization", sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2001-03-01"))
                .andExpect(jsonPath("$.reservasPorEstado.CONFIRMADA").value(2))
                .andExpect(jsonPath("$.reservasPorEstado.COMPLETADA").value(1))
                .andExpect(jsonPath("$.reservasPorEstado.CANCELADA").value(1))
                .andExpect(jsonPath("$.reservasPorEstado.EN_REVISION").value(0))
                .andExpect(jsonPath("$.ingresosEstimados").value(60000))
                .andExpect(jsonPath("$.ciudadesConMayorDemanda", contains("Medellín de prueba", "Santa Marta de prueba")))
                .andExpect(jsonPath("$.usuarios").isNumber())
                .andExpect(jsonPath("$.publicacionesActivas").isNumber());
    }

    @Test
    void sinFechasMuestraLosUltimosTreintaDias() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").exists())
                .andExpect(jsonPath("$.hasta").exists());
    }

    @Test
    void unPeriodoAlRevesSeRechaza() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard").param("desde", "2001-03-31").param("hasta", "2001-03-01")
                        .header("Authorization", sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR)))
                .andExpect(status().isBadRequest());
    }

    private UUID publicacionEn(UUID anfitrion, String ciudad) {
        UUID publicacion = crearPublicacionActiva(anfitrion);
        comoAplicacion.update("UPDATE publicacion SET ciudad = ? WHERE id = ?", ciudad, publicacion);
        return publicacion;
    }

    private void reservar(UUID huesped, UUID publicacion, String llegada, String estado, int tarifaServicio, String hechaEl) {
        comoAplicacion.update("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado,
                                     tarifa_servicio, total, creado_en)
                VALUES (?, ?, CAST(? AS date), CAST(? AS date) + 2, 1, ?, ?, ?, CAST(? AS date) + time '12:00' AT TIME ZONE 'America/Bogota')""",
                huesped, publicacion, llegada, llegada, estado, tarifaServicio, tarifaServicio * 10, hechaEl);
    }
}
