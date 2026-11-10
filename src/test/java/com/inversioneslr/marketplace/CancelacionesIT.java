package com.inversioneslr.marketplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** CU-20 · RF-22, RF-23. La reserva cuesta 330.000, con 30.000 de tarifa de servicio. */
class CancelacionesIT extends PruebaIntegracion {

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
    void elHuespedVeCuantoLeDevuelvenAntesDeCancelar() throws Exception {
        UUID reserva = reservar(10, "CONFIRMADA");

        mvc.perform(get("/api/v1/reservas/{id}/cancelacion/estimacion", reserva).header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorDevolucion").value(300000))
                .andExpect(jsonPath("$.politica").value("Flexible"));
    }

    @Test
    void entreCincoYDosDiasAntesSeDevuelveLaMitad() throws Exception {
        UUID reserva = reservar(3, "CONFIRMADA");

        mvc.perform(get("/api/v1/reservas/{id}/cancelacion/estimacion", reserva).header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(jsonPath("$.valorDevolucion").value(150000));
    }

    @Test
    void otroHuespedNoPuedeConsultarLaEstimacion() throws Exception {
        UUID reserva = reservar(10, "CONFIRMADA");
        UUID otro = crearUsuario(Rol.HUESPED);

        mvc.perform(get("/api/v1/reservas/{id}/cancelacion/estimacion", reserva).header("Authorization", sesionDe(otro, Rol.HUESPED)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaReservaYaCanceladaNoSeEstima() throws Exception {
        UUID reserva = reservar(10, "CANCELADA");

        mvc.perform(get("/api/v1/reservas/{id}/cancelacion/estimacion", reserva).header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(status().isConflict());
    }

    /** @param diasAntes cuántos días faltan, desde hoy, para la llegada */
    UUID reservar(int diasAntes, String estado) {
        LocalDate llegada = LocalDate.now().plusDays(diasAntes);
        return comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado,
                                     noches, precio_base, tarifa_limpieza, tarifa_servicio, descuento, total)
                VALUES (?, ?, ?, ?, 2, ?, 3, 300000, 0, 30000, 0, 330000) RETURNING id""",
                UUID.class, huesped, publicacion, llegada, llegada.plusDays(3), estado);
    }
}
