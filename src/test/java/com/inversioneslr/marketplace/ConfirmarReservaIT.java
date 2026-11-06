package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
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
import com.inversioneslr.marketplace.reservas.aplicacion.ServicioReservas;
import com.inversioneslr.marketplace.reservas.aplicacion.ServicioReservas.ConfirmarReservaComando;
import com.inversioneslr.marketplace.reservas.dominio.PeriodoNoDisponibleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/** La cotización es de SCRUM-32, así que se simula. La pasarela se simula para provocar sus fallas. */
class ConfirmarReservaIT extends PruebaIntegracion {

    private static final BigDecimal TOTAL = new BigDecimal("330000.00");

    @Autowired
    ServicioReservas reservas;

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

    /** RNF-13: la base de datos deja pasar exactamente una, y a las demás se les devuelve lo cobrado. */
    @Test
    void conCincuentaSolicitudesSimultaneasSoloSeConfirmaUna() throws Exception {
        int solicitudes = 50;
        ConfirmarReservaComando comando = new ConfirmarReservaComando(publicacion, LocalDate.of(2030, 7, 10), LocalDate.of(2030, 7, 13), 2, List.of());
        CountDownLatch salida = new CountDownLatch(1);
        AtomicInteger confirmadas = new AtomicInteger();
        AtomicInteger rechazadas = new AtomicInteger();

        try (ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < solicitudes; i++) {
                hilos.submit(() -> {
                    salida.await();
                    try {
                        reservas.confirmarReserva(huesped, comando, UUID.randomUUID().toString());
                        confirmadas.incrementAndGet();
                    } catch (PeriodoNoDisponibleException e) {
                        rechazadas.incrementAndGet();
                    }
                    return null;
                });
            }
            salida.countDown();
        }

        assertThat(confirmadas).hasValue(1);
        assertThat(rechazadas).hasValue(solicitudes - 1);
        assertThat(contar("SELECT count(*) FROM reserva WHERE publicacion_id = ? AND estado = 'CONFIRMADA'", publicacion)).isEqualTo(1);
        assertThat(llamadasA("cobrar") - llamadasA("devolver")).isEqualTo(1);
    }

    @Test
    void siLaDevolucionFallaQuedaEnAuditoria() {
        ConfirmarReservaComando comando = new ConfirmarReservaComando(publicacion, LocalDate.of(2030, 8, 10), LocalDate.of(2030, 8, 13), 2, List.of());
        doThrow(new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO, "No se procesó")).when(pasarela).devolver(any(), any());
        // Otra reserva toma el periodo mientras se cobra: la verificación previa pasó, pero la base de datos rechaza.
        when(pasarela.cobrar(any(), any())).thenAnswer(llamada -> {
            comoAplicacion.update("""
                    INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                    VALUES (?, ?, '2030-08-10', '2030-08-13', 1, 'CONFIRMADA')""", huesped, publicacion);
            return "pi_" + llamada.getArgument(1);
        });

        assertThatThrownBy(() -> reservas.confirmarReserva(huesped, comando, UUID.randomUUID().toString()))
                .isInstanceOf(PeriodoNoDisponibleException.class);

        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'DevolucionFallida'", publicacion)).isEqualTo(1);
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

    private long llamadasA(String metodo) {
        return mockingDetails(pasarela).getInvocations().stream()
                .filter(llamada -> llamada.getMethod().getName().equals(metodo))
                .count();
    }

    private long reservasDeLaPublicacion() {
        return contar("SELECT count(*) FROM reserva WHERE publicacion_id = ?", publicacion);
    }

    private long contar(String sql, Object valor) {
        return comoAplicacion.queryForObject(sql, Long.class, valor);
    }
}
