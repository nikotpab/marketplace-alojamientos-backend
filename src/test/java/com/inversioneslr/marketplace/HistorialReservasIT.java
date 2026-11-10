package com.inversioneslr.marketplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** CU-18 · RF-20. */
class HistorialReservasIT extends PruebaIntegracion {

    UUID huesped;
    UUID anfitrion;
    UUID publicacion;

    @BeforeEach
    void preparar() {
        huesped = crearUsuario(Rol.HUESPED);
        anfitrion = crearUsuario(Rol.ANFITRION);
        publicacion = crearPublicacionActiva(anfitrion);
    }

    @Test
    void elHuespedVeSusReservasDeLaMasRecienteALaMasAntiguaSinDatosDeContacto() throws Exception {
        reservar(huesped, "2030-02-01", "2030-02-03", "CANCELADA");
        reservar(huesped, "2030-03-01", "2030-03-04", "CONFIRMADA");
        reservar(crearUsuario(Rol.HUESPED), "2030-04-01", "2030-04-02", "CONFIRMADA");

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].llegada").value("2030-03-01"))
                .andExpect(jsonPath("$[0].estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$[0].alojamiento").value("Casa de prueba"))
                .andExpect(jsonPath("$[0].codigo").exists())
                .andExpect(jsonPath("$[0].contactoHuesped").doesNotExist())
                .andExpect(jsonPath("$[1].estado").value("CANCELADA"));
    }

    @Test
    void sePuedeFiltrarPorEstado() throws Exception {
        reservar(huesped, "2030-02-01", "2030-02-03", "CANCELADA");
        reservar(huesped, "2030-03-01", "2030-03-04", "CONFIRMADA");

        mvc.perform(get("/api/v1/reservas").param("estado", "CANCELADA").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("CANCELADA"));
    }

    @Test
    void elAnfitrionVeElContactoDelHuespedSoloEnLasConfirmadas() throws Exception {
        reservar(huesped, "2030-03-01", "2030-03-04", "CONFIRMADA");
        reservar(huesped, "2030-02-01", "2030-02-03", "CANCELADA");
        String correo = comoAplicacion.queryForObject("SELECT correo FROM usuario WHERE id = ?", String.class, huesped);

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].contactoHuesped.correo").value(correo))
                .andExpect(jsonPath("$[0].contactoHuesped.telefono").value("3000000000"))
                .andExpect(jsonPath("$[1].contactoHuesped").doesNotExist());
    }

    @Test
    void unAnfitrionNoVeLasReservasDeAlojamientosAjenos() throws Exception {
        reservar(huesped, "2030-03-01", "2030-03-04", "CONFIRMADA");

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(crearUsuario(Rol.ANFITRION), Rol.ANFITRION)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elAdministradorNoUsaEsteHistorial() throws Exception {
        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR)))
                .andExpect(status().isForbidden());
    }

    private void reservar(UUID quien, String llegada, String salida, String estado) {
        comoAplicacion.update("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado, noches, total)
                VALUES (?, ?, CAST(? AS date), CAST(? AS date), 2, ?, 2, 200000)""", quien, publicacion, llegada, salida, estado);
    }
}
