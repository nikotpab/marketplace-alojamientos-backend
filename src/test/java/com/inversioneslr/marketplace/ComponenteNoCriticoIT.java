package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.infraestructura.ProcesadorEventos;
import com.inversioneslr.marketplace.notificaciones.dominio.RepositorioNotificaciones;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/**
 * SCRUM-63 · ASR-05 · RNF-10: las notificaciones son un componente no crítico. Lentas o caídas, no frenan la búsqueda,
 * la reserva ni la cancelación, y los avisos pendientes se entregan cuando vuelven.
 */
class ComponenteNoCriticoIT extends PruebaIntegracion {

    @MockitoBean
    RepositorioNotificaciones notificaciones;

    @Autowired
    ProcesadorEventos procesador;

    @Test
    void conLasNotificacionesTardandoMasDeDiezSegundosLosCasosCriticosNoEsperan() throws Exception {
        doAnswer(llamada -> {
            Thread.sleep(11_000);
            return null;
        }).when(notificaciones).guardar(any());
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));

        long inicio = System.nanoTime();
        String respuesta = confirmar(huesped, publicacion).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(desde(inicio)).isLessThan(Duration.ofSeconds(5));

        inicio = System.nanoTime();
        mvc.perform(post("/api/v1/reservas/{id}/cancelacion", (String) JsonPath.read(respuesta, "$.id"))
                        .header("Authorization", sesionDe(huesped, Rol.HUESPED))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Cambié de planes\"}"))
                .andExpect(status().isOk());
        assertThat(desde(inicio)).isLessThan(Duration.ofSeconds(4));

        inicio = System.nanoTime();
        mvc.perform(get("/api/v1/alojamientos").param("ciudad", "Cartagena")).andExpect(status().isOk());
        assertThat(desde(inicio)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void conLasNotificacionesCaidasLaReservaSeConfirmaYLosAvisosSeEntreganAlVolver() throws Exception {
        doThrow(new IllegalStateException("Notificaciones caídas")).when(notificaciones).guardar(any());
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));

        String respuesta = confirmar(huesped, publicacion).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reserva = JsonPath.read(respuesta, "$.id");

        // La falla queda registrada: la entrega figura FAILED en el registro de eventos.
        await().until(() -> "FAILED".equals(estadoDeLaEntrega(reserva)));

        doNothing().when(notificaciones).guardar(any());
        procesador.reintentarFallidos();

        esperarEventosDe(UUID.fromString(reserva));
        verify(notificaciones, atLeast(2)).guardar(any());
    }

    private ResultActions confirmar(UUID huesped, UUID publicacion) throws Exception {
        return mvc.perform(post("/api/v1/reservas")
                .header("Authorization", sesionDe(huesped, Rol.HUESPED))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"publicacionId":"%s","llegada":"2030-06-10","salida":"2030-06-13","huespedes":2}""".formatted(publicacion)));
    }

    private String estadoDeLaEntrega(String reserva) {
        return comoAplicacion.queryForObject("""
                SELECT status FROM event_publication
                WHERE serialized_event::jsonb ->> 'entidadId' = ? AND serialized_event::jsonb ->> 'tipo' = 'ReservaConfirmada'""",
                String.class, reserva);
    }

    private static Duration desde(long inicio) {
        return Duration.ofNanos(System.nanoTime() - inicio);
    }
}
