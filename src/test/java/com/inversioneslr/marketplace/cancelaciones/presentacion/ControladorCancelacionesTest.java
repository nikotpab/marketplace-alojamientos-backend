package com.inversioneslr.marketplace.cancelaciones.presentacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.cancelaciones.aplicacion.ServicioCancelaciones;
import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion;
import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion.EstadoFinal;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.Tokens;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import com.inversioneslr.marketplace.compartido.presentacion.ConfiguracionSeguridad;
import com.inversioneslr.marketplace.compartido.presentacion.ManejadorDenegaciones;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ControladorCancelaciones.class)
@Import({ConfiguracionSeguridad.class, ManejadorDenegaciones.class})
class ControladorCancelacionesTest {

    @Autowired MockMvc mvc;
    @MockitoBean ServicioCancelaciones servicio;
    @MockitoBean Tokens tokens;
    @MockitoBean PublicadorEventos eventos;

    private static final UUID HUESPED_ID = UUID.randomUUID();
    private static final UUID RESERVA_ID = UUID.randomUUID();

    @BeforeEach
    void configurarTokens() {
        lenient().when(tokens.verificar("token-huesped"))
                .thenReturn(Optional.of(new UsuarioAutenticado(HUESPED_ID, Rol.HUESPED)));
        lenient().when(tokens.verificar("token-anfitrion"))
                .thenReturn(Optional.of(new UsuarioAutenticado(UUID.randomUUID(), Rol.ANFITRION)));
    }

    @Test
    void sinTokenLaEstimacionResponde401() throws Exception {
        mvc.perform(get("/api/v1/reservas/{id}/cancelacion/estimacion", RESERVA_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unAnfitrionNoEstima() throws Exception {
        mvc.perform(get("/api/v1/reservas/{id}/cancelacion/estimacion", RESERVA_ID)
                .header("Authorization", "Bearer token-anfitrion"))
                .andExpect(status().isForbidden());
    }

    @Test
    void estimaDevolucionParaElHuesped() throws Exception {
        when(servicio.estimarDevolucion(eq(HUESPED_ID), eq(RESERVA_ID)))
                .thenReturn(new ServicioCancelaciones.EstimacionDevolucion(new BigDecimal("300000"), "Flexible"));

        mvc.perform(get("/api/v1/reservas/{id}/cancelacion/estimacion", RESERVA_ID)
                .header("Authorization", "Bearer token-huesped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorDevolucion").value(300000))
                .andExpect(jsonPath("$.politica").value("Flexible"));
    }

    @Test
    void cancelaYDevuelveLaCancelacion() throws Exception {
        when(servicio.cancelarReserva(eq(HUESPED_ID), eq(RESERVA_ID), any()))
                .thenReturn(new Cancelacion(UUID.randomUUID(), RESERVA_ID, HUESPED_ID,
                        Instant.now(), "Cambio de planes",
                        new BigDecimal("300000"), EstadoFinal.DEVOLUCION_PROCESADA));

        mvc.perform(post("/api/v1/reservas/{id}/cancelacion", RESERVA_ID)
                .header("Authorization", "Bearer token-huesped")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Cambio de planes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoFinal").value("DEVOLUCION_PROCESADA"))
                .andExpect(jsonPath("$.valorDevolucion").value(300000));
    }

    @Test
    void motivoVacioResponde400() throws Exception {
        mvc.perform(post("/api/v1/reservas/{id}/cancelacion", RESERVA_ID)
                .header("Authorization", "Bearer token-huesped")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.motivo").exists());
    }
}
