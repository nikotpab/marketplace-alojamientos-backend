package com.inversioneslr.marketplace.reservas.presentacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.Tokens;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import com.inversioneslr.marketplace.compartido.presentacion.ConfiguracionSeguridad;
import com.inversioneslr.marketplace.compartido.presentacion.ManejadorDenegaciones;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import com.inversioneslr.marketplace.reservas.aplicacion.ServicioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

@WebMvcTest(ControladorReservas.class)
@Import({ConfiguracionSeguridad.class, ManejadorDenegaciones.class})
class ControladorReservasTest {

    @Autowired MockMvc mvc;
    @MockitoBean ServicioReservas servicio;
    @MockitoBean Tokens tokens;
    @MockitoBean PublicadorEventos eventos;

    private static final UUID HUESPED_ID = UUID.randomUUID();

    @BeforeEach
    void configurarTokens() {
        lenient().when(tokens.verificar("token-huesped"))
                .thenReturn(Optional.of(new UsuarioAutenticado(HUESPED_ID, Rol.HUESPED)));
        lenient().when(tokens.verificar("token-anfitrion"))
                .thenReturn(Optional.of(new UsuarioAutenticado(UUID.randomUUID(), Rol.ANFITRION)));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(post("/api/v1/reservas")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoValido()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unAnfitrionRecibe403() throws Exception {
        mvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer token-anfitrion")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoValido()))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinIdempotencyKeyResponde400() throws Exception {
        mvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer token-huesped")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoValido()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void camposObligatoriosFaltantesResponden400() throws Exception {
        mvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer token-huesped")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.publicacionId").exists())
                .andExpect(jsonPath("$.campos.llegada").exists())
                .andExpect(jsonPath("$.campos.salida").exists())
                .andExpect(jsonPath("$.campos.huespedes").exists());
    }

    @Test
    void confirmaYDevuelve201() throws Exception {
        when(servicio.confirmarReserva(any(), any(), any())).thenReturn(reservaConfirmada());

        mvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer token-huesped")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.codigo").value("RES-001"));
    }

    @Test
    void consultarHistorialDevuelveLista() throws Exception {
        when(servicio.consultarHistorial(any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/reservas").header("Authorization", "Bearer token-huesped"))
                .andExpect(status().isOk());
    }

    private static String cuerpoValido() {
        return """
                {"publicacionId":"%s","llegada":"2030-06-01","salida":"2030-06-05","huespedes":2,"adicionales":[]}"""
                .formatted(UUID.randomUUID());
    }

    private static Reserva reservaConfirmada() {
        return new Reserva(UUID.randomUUID(), "RES-001", HUESPED_ID, UUID.randomUUID(),
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 5),
                2, EstadoReserva.CONFIRMADA, 4,
                new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("10000"),
                BigDecimal.ZERO, new BigDecimal("110000"));
    }
}
