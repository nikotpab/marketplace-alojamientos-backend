package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.cotizacion.Cotizacion;
import com.inversioneslr.marketplace.cotizacion.aplicacion.ServicioCotizacion;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import com.inversioneslr.marketplace.reservas.aplicacion.ServicioReservas;
import com.inversioneslr.marketplace.reservas.aplicacion.ServicioReservas.ConfirmarReservaComando;
import com.inversioneslr.marketplace.reservas.dominio.PeriodoNoDisponibleException;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class IdempotenciaReservaIT extends PruebaIntegracion {

    private static final BigDecimal TOTAL = new BigDecimal("330000.00");

    @Autowired
    ServicioReservas reservas;

    /** Con un total fijo, para que estas pruebas no dependan del cálculo de la cotización. */
    @MockitoBean
    ServicioCotizacion cotizacion;

    @MockitoBean
    PasarelaPagos pasarela;

    private UUID huesped;
    private UUID publicacion;
    private ConfirmarReservaComando comando;

    @BeforeEach
    void preparar() {
        huesped = crearUsuario(Rol.HUESPED);
        publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        comando = new ConfirmarReservaComando(publicacion, LocalDate.of(2030, 9, 10), LocalDate.of(2030, 9, 13), 2, List.of());
        when(cotizacion.cotizar(any(), any(), any(), any())).thenReturn(new Cotizacion(3, new BigDecimal("300000.00"),
                BigDecimal.ZERO, new BigDecimal("30000.00"), BigDecimal.ZERO, BigDecimal.ZERO, TOTAL));
        // Como Stripe: la misma clave siempre produce el mismo pago.
        when(pasarela.cobrar(any(), any())).thenAnswer(llamada -> "pi_" + llamada.getArgument(1));
    }

    @Test
    void guardaLaClaveConLaReservaEnLaMismaTransaccion() {
        String clave = UUID.randomUUID().toString();

        Reserva reserva = reservas.confirmarReserva(huesped, comando, clave);

        var solicitud = comoAplicacion.queryForMap("SELECT huesped_id, reserva_id, resultado::text AS resultado FROM solicitud_procesada WHERE clave = ?", clave);
        assertThat(solicitud.get("huesped_id")).isEqualTo(huesped);
        assertThat(solicitud.get("reserva_id")).isEqualTo(reserva.id());
        assertThat(solicitud.get("resultado").toString()).contains("CONFIRMADA");
    }

    @Test
    void unReintentoRecibeElResultadoOriginalSinCobrarDeNuevo() {
        String clave = UUID.randomUUID().toString();

        Reserva original = reservas.confirmarReserva(huesped, comando, clave);
        Reserva repetida = reservas.confirmarReserva(huesped, comando, clave);

        assertThat(repetida).isEqualTo(original);
        verify(pasarela, times(1)).cobrar(any(), any());
        assertThat(contar("SELECT count(*) FROM reserva WHERE publicacion_id = ?", publicacion)).isEqualTo(1);
    }

    /** RNF-14: 10 envíos de la misma solicitud producen 1 reserva, 1 pago y 1 notificación por destinatario. */
    @Test
    void diezEnviosSimultaneosProducenUnSoloEfecto() throws Exception {
        String clave = UUID.randomUUID().toString();
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Reserva>> envios = new ArrayList<>();

        // Hilos de plataforma, como los de Tomcat.
        try (ExecutorService hilos = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                envios.add(hilos.submit(() -> {
                    salida.await();
                    return reservas.confirmarReserva(huesped, comando, clave);
                }));
            }
            salida.countDown();
        }

        Set<UUID> resultados = new HashSet<>();
        for (Future<Reserva> envio : envios) {
            resultados.add(envio.get().id());
        }
        UUID reserva = resultados.iterator().next();
        esperarEventosDe(reserva);

        assertThat(resultados).hasSize(1);
        assertThat(contar("SELECT count(*) FROM reserva WHERE publicacion_id = ?", publicacion)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM pago WHERE clave_idempotencia = ?", clave)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'ReservaConfirmada'", reserva)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ?", huesped)).isEqualTo(1);
        verify(pasarela, never()).devolver(any(), any());
    }

    @Test
    void laClaveDeOtroHuespedSeDeniega() {
        String clave = UUID.randomUUID().toString();
        reservas.confirmarReserva(huesped, comando, clave);
        UUID otroHuesped = crearUsuario(Rol.HUESPED);

        assertThatThrownBy(() -> reservas.confirmarReserva(otroHuesped, comando, clave))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.ACCESO_DENEGADO));
    }

    @Test
    void unReintentoTrasPerderElPeriodoRecibeElMismoRechazoSinCobrarDeNuevo() {
        String clave = UUID.randomUUID().toString();
        // Otra reserva toma el periodo mientras se cobra.
        when(pasarela.cobrar(any(), any())).thenAnswer(llamada -> {
            comoAplicacion.update("""
                    INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                    VALUES (?, ?, '2030-09-10', '2030-09-13', 1, 'CONFIRMADA')""", huesped, publicacion);
            return "pi_" + llamada.getArgument(1);
        });

        assertThatThrownBy(() -> reservas.confirmarReserva(huesped, comando, clave)).isInstanceOf(PeriodoNoDisponibleException.class);
        assertThatThrownBy(() -> reservas.confirmarReserva(huesped, comando, clave)).isInstanceOf(PeriodoNoDisponibleException.class);

        verify(pasarela, times(1)).cobrar(any(), any());
        verify(pasarela, times(1)).devolver(any(), any());
    }

    @Test
    void lasClavesSeConservanVeinticuatroHorasYLuegoSeEliminan() {
        String vigente = UUID.randomUUID().toString();
        String vencida = UUID.randomUUID().toString();
        reservas.confirmarReserva(huesped, comando, vigente);
        comoAplicacion.update("INSERT INTO solicitud_procesada (clave, huesped_id, expira_en) VALUES (?, ?, now() - interval '1 minute')", vencida, huesped);

        assertThat(comoAplicacion.queryForObject("SELECT expira_en > now() + interval '23 hours' FROM solicitud_procesada WHERE clave = ?", Boolean.class, vigente)).isTrue();

        reservas.eliminarSolicitudesVencidas();

        assertThat(contar("SELECT count(*) FROM solicitud_procesada WHERE clave = ?", vencida)).isZero();
        assertThat(contar("SELECT count(*) FROM solicitud_procesada WHERE clave = ?", vigente)).isEqualTo(1);
    }

    long contar(String sql, Object valor) {
        return comoAplicacion.queryForObject(sql, Long.class, valor);
    }
}
