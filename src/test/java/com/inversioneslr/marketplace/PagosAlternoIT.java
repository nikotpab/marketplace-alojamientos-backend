package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** ASR-08b: el flujo de pago completo funciona igual con el segundo proveedor, sin tocar dominio ni aplicación. */
@TestPropertySource(properties = "marketplace.pagos.proveedor=alterno")
class PagosAlternoIT extends PruebaIntegracion {

    @Test
    void conElProveedorAlternoSeConfirmaLaReservaYElPagoQuedaRegistrado() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));

        String respuesta = confirmar(huesped, publicacion, UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andReturn().getResponse().getContentAsString();

        UUID reserva = UUID.fromString(JsonPath.read(respuesta, "$.id"));
        assertThat(comoAplicacion.queryForObject("SELECT id_pago_stripe FROM pago WHERE reserva_id = ? AND estado = 'APROBADO'",
                String.class, reserva)).startsWith("alterno-");
    }

    @Test
    void unReintentoConLaMismaClaveNoCobraDosVeces() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        String clave = UUID.randomUUID().toString();

        confirmar(huesped, publicacion, clave).andExpect(status().isCreated());
        confirmar(huesped, publicacion, clave).andExpect(status().isCreated());

        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM pago WHERE clave_idempotencia = ?", Long.class, clave)).isEqualTo(1);
    }

    @Test
    void unCobroQueElProveedorRechazaNoCreaLaReserva() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        comoAplicacion.update("UPDATE publicacion SET precio_noche = 9000000 WHERE id = ?", publicacion);

        confirmar(huesped, publicacion, UUID.randomUUID().toString()).andExpect(status().isPaymentRequired());

        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM reserva WHERE publicacion_id = ?", Long.class, publicacion)).isZero();
    }

    private ResultActions confirmar(UUID huesped, UUID publicacion, String clave) throws Exception {
        return mvc.perform(post("/api/v1/reservas")
                .header("Authorization", sesionDe(huesped, Rol.HUESPED))
                .header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"publicacionId":"%s","llegada":"2030-06-10","salida":"2030-06-13","huespedes":2}""".formatted(publicacion)));
    }
}
