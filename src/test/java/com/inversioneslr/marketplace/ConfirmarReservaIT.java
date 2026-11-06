package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.inversioneslr.marketplace.cotizacion.api.Cotizacion;
import com.inversioneslr.marketplace.cotizacion.api.FachadaCotizacion;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/** La cotización es de SCRUM-32, así que se simula. La pasarela se simula para provocar sus fallas. */
class ConfirmarReservaIT extends PruebaIntegracion {

    private static final BigDecimal TOTAL = new BigDecimal("330000.00");

    @MockitoBean
    FachadaCotizacion cotizacion;

    @MockitoBean
    PasarelaPagos pasarela;

    private UUID huesped;
    private UUID publicacion;

    @BeforeEach
    void preparar() {
        huesped = crearUsuario(Rol.HUESPED);
        publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        when(cotizacion.cotizar(any(), any(), any(), any())).thenReturn(new Cotizacion(3, new BigDecimal("300000.00"),
                BigDecimal.ZERO, new BigDecimal("30000.00"), BigDecimal.ZERO, BigDecimal.ZERO, TOTAL));
        when(pasarela.cobrar(any(), any())).thenAnswer(llamada -> "pi_" + llamada.getArgument(1));
    }

    @Test
    void confirmaLaReservaConPagoAuditoriaYEvento() throws Exception {
        String clave = UUID.randomUUID().toString();

        String reserva = confirmar(clave, "2030-06-10", "2030-06-13", 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.total").value(330000.00))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        UUID id = UUID.fromString(reserva);
        assertThat(contar("SELECT count(*) FROM reserva WHERE id = ? AND estado = 'CONFIRMADA'", id)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM pago WHERE reserva_id = ? AND estado = 'APROBADO'", id)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'ReservaConfirmada'", id)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM evento_pendiente WHERE payload->>'entidadId' = ?", id.toString())).isEqualTo(1);
        verify(pasarela).cobrar(TOTAL, clave);
    }

    @Test
    void pagoRechazadoNoCreaLaReservaYSeAudita() throws Exception {
        when(pasarela.cobrar(any(), any())).thenThrow(new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_RECHAZADO, "No aprobado"));

        confirmar(UUID.randomUUID().toString(), "2030-06-10", "2030-06-13", 2).andExpect(status().isPaymentRequired());

        assertThat(reservasDeLaPublicacion()).isZero();
        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'PagoFallido' AND resultado = 'PAGO_RECHAZADO'", publicacion)).isEqualTo(1);
    }

    @Test
    void pagoNoProcesadoNoCreaLaReservaNiBloqueaElPeriodo() throws Exception {
        when(pasarela.cobrar(any(), any())).thenThrow(new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO, "No se procesó"));

        confirmar(UUID.randomUUID().toString(), "2030-06-10", "2030-06-13", 2)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PAGO_NO_PROCESADO"));

        assertThat(reservasDeLaPublicacion()).isZero();
    }

    @Test
    void publicacionNoActivaNoSeCobra() throws Exception {
        comoAplicacion.update("UPDATE publicacion SET estado = 'PAUSADA' WHERE id = ?", publicacion);

        confirmar(UUID.randomUUID().toString(), "2030-06-10", "2030-06-13", 2).andExpect(status().isConflict());

        verify(pasarela, never()).cobrar(any(), any());
    }

    @Test
    void periodoOcupadoNoSeCobra() throws Exception {
        confirmar(UUID.randomUUID().toString(), "2030-06-10", "2030-06-13", 2).andExpect(status().isCreated());

        confirmar(UUID.randomUUID().toString(), "2030-06-11", "2030-06-14", 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Periodo no disponible."));

        assertThat(reservasDeLaPublicacion()).isEqualTo(1);
    }

    @Test
    void fechasInvalidasSeRechazanIndicandoElCampo() throws Exception {
        confirmar(UUID.randomUUID().toString(), "2030-06-13", "2030-06-10", 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.fechaSalida").exists());
    }

    @Test
    void superarLaCapacidadSeRechazaIndicandoElCampo() throws Exception {
        confirmar(UUID.randomUUID().toString(), "2030-06-10", "2030-06-13", 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.numeroHuespedes").exists());
    }

    @Test
    void soloUnHuespedPuedeReservar() throws Exception {
        String anfitrion = sesionDe(crearUsuario(Rol.ANFITRION), Rol.ANFITRION);

        mvc.perform(post("/api/v1/reservas").header("Authorization", anfitrion).header("Idempotency-Key", "clave")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("2030-06-10", "2030-06-13", 2)))
                .andExpect(status().isForbidden());
    }

    private ResultActions confirmar(String clave, String llegada, String salida, int huespedes) throws Exception {
        return mvc.perform(post("/api/v1/reservas")
                .header("Authorization", sesionDe(huesped, Rol.HUESPED))
                .header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(llegada, salida, huespedes)));
    }

    private String cuerpo(String llegada, String salida, int huespedes) {
        return """
                {"publicacionId":"%s","fechaLlegada":"%s","fechaSalida":"%s","numeroHuespedes":%d}"""
                .formatted(publicacion, llegada, salida, huespedes);
    }

    private long reservasDeLaPublicacion() {
        return contar("SELECT count(*) FROM reserva WHERE publicacion_id = ?", publicacion);
    }

    private long contar(String sql, Object valor) {
        return comoAplicacion.queryForObject(sql, Long.class, valor);
    }
}
