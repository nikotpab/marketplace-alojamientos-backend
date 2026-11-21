package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Con la pasarela simulada real, que como Stripe rechaza los métodos que fallan a propósito. */
class MetodoPagoIT extends PruebaIntegracion {

    private UUID huesped;
    private UUID publicacion;

    @BeforeEach
    void preparar() {
        huesped = crearUsuario(Rol.HUESPED);
        publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
    }

    @Test
    void unMetodoQueRechazaElPagoNoConfirmaLaReservaYElPeriodoQuedaLibre() throws Exception {
        String clave = UUID.randomUUID().toString();

        reservar(clave, "pm_card_chargeDeclined")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.codigo").value("PAGO_RECHAZADO"));

        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM reserva WHERE publicacion_id = ?", Long.class, publicacion)).isZero();
        assertThat(comoAplicacion.queryForObject("SELECT estado FROM pago WHERE clave_idempotencia = ?", String.class, clave))
                .isEqualTo("RECHAZADO");
        reservar(UUID.randomUUID().toString(), "pm_card_visa").andExpect(status().isCreated());
    }

    @Test
    void sinMetodoDePagoSeUsaElPredeterminado() throws Exception {
        mvc.perform(post("/api/v1/reservas")
                        .header("Authorization", sesionDe(huesped, Rol.HUESPED))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publicacionId":"%s","llegada":"2030-06-10","salida":"2030-06-13","huespedes":2}"""
                                .formatted(publicacion)))
                .andExpect(status().isCreated());
    }

    private ResultActions reservar(String clave, String metodoPagoId) throws Exception {
        return mvc.perform(post("/api/v1/reservas")
                .header("Authorization", sesionDe(huesped, Rol.HUESPED))
                .header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"publicacionId":"%s","llegada":"2030-06-10","salida":"2030-06-13","huespedes":2,"metodoPagoId":"%s"}"""
                        .formatted(publicacion, metodoPagoId)));
    }
}
