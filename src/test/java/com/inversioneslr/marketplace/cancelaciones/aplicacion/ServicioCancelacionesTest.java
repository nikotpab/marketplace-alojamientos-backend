package com.inversioneslr.marketplace.cancelaciones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.inversioneslr.marketplace.cancelaciones.aplicacion.ServicioCancelaciones.CancelarReservaComando;
import com.inversioneslr.marketplace.cancelaciones.aplicacion.ServicioCancelaciones.EstimacionDevolucion;
import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion;
import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion.EstadoFinal;
import com.inversioneslr.marketplace.cancelaciones.dominio.RepositorioCancelaciones;
import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicacionId;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.ReservaId;
import com.inversioneslr.marketplace.pagos.FachadaPagos;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import com.inversioneslr.marketplace.reservas.FachadaReservas;
import com.inversioneslr.marketplace.reservas.FachadaReservas.DatosReserva;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** SCRUM-141: CU-20 sin base de datos ni Docker, con las fachadas de reservas, publicaciones y pagos simuladas. */
@ExtendWith(MockitoExtension.class)
class ServicioCancelacionesTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate HOY = LocalDate.now(RELOJ);
    private static final UUID HUESPED = UUID.randomUUID();
    private static final UUID ANFITRION = UUID.randomUUID();
    private static final UUID PUBLICACION = UUID.randomUUID();
    private static final UUID RESERVA = UUID.randomUUID();
    private static final BigDecimal TOTAL = new BigDecimal("330000");
    private static final BigDecimal TARIFA_SERVICIO = new BigDecimal("30000");
    /** Flexible con 5 días o más: todo menos la tarifa de servicio. */
    private static final BigDecimal DEVOLUCION_TOTAL = new BigDecimal("300000");
    private static final CancelarReservaComando COMANDO = new CancelarReservaComando("Cambio de planes");

    @Mock
    private RepositorioCancelaciones cancelaciones;
    @Mock
    private FachadaReservas reservas;
    @Mock
    private FachadaPublicaciones publicaciones;
    @Mock
    private FachadaPagos pagos;
    @Mock
    private PublicadorEventos eventos;
    @Mock
    private PlatformTransactionManager transacciones;

    private ServicioCancelaciones servicio;

    @BeforeEach
    void preparar() {
        servicio = new ServicioCancelaciones(cancelaciones, reservas, publicaciones, pagos, eventos,
                new TransactionTemplate(transacciones), RELOJ);
    }

    @Test
    void laEstimacionAplicaLaPoliticaDeLaPublicacionSinCancelarNada() {
        prepararReserva(HOY.plusDays(10), EstadoReserva.CONFIRMADA, HUESPED, "Flexible");

        EstimacionDevolucion estimacion = servicio.estimarDevolucion(HUESPED, RESERVA);

        assertThat(estimacion.valorDevolucion()).isEqualByComparingTo(DEVOLUCION_TOTAL);
        assertThat(estimacion.politica()).isEqualTo("Flexible");
        verifyNoInteractions(pagos, cancelaciones, eventos);
        verify(reservas, never()).marcarCancelada(any());
    }

    @Test
    void conLaDevolucionProcesadaCancelaLaReservaYPublicaLosDosEventos() {
        prepararReserva(HOY.plusDays(10), EstadoReserva.CONFIRMADA, HUESPED, "Flexible");

        Cancelacion cancelacion = servicio.cancelarReserva(HUESPED, RESERVA, COMANDO);

        assertThat(cancelacion.estadoFinal()).isEqualTo(EstadoFinal.DEVOLUCION_PROCESADA);
        assertThat(cancelacion.valorDevolucion()).isEqualByComparingTo(DEVOLUCION_TOTAL);
        verify(pagos).devolverPagoDeReserva(new ReservaId(RESERVA), DEVOLUCION_TOTAL);
        verify(reservas).marcarCancelada(new ReservaId(RESERVA));
        verify(cancelaciones).guardar(cancelacion);

        List<EventoDominio> publicados = eventosPublicados(2);
        assertThat(publicados.get(0).tipo()).isEqualTo("ReservaCancelada");
        assertThat(publicados.get(0).resultado()).isEqualTo("DEVOLUCION_PROCESADA");
        assertThat(publicados.get(0).destinatarios()).containsExactly(HUESPED, ANFITRION);
        assertThat(publicados.get(0).detalle()).containsEntry("motivo", "Cambio de planes");
        assertThat(publicados.get(1).tipo()).isEqualTo("DevolucionProcesada");
    }

    @Test
    void siLaDevolucionFallaLaReservaSeCancelaIgualYQuedaDevolucionFallida() {
        prepararReserva(HOY.plusDays(10), EstadoReserva.CONFIRMADA, HUESPED, "Flexible");
        doThrow(new IllegalStateException("La pasarela no responde")).when(pagos).devolverPagoDeReserva(new ReservaId(RESERVA), DEVOLUCION_TOTAL);

        Cancelacion cancelacion = servicio.cancelarReserva(HUESPED, RESERVA, COMANDO);

        assertThat(cancelacion.estadoFinal()).isEqualTo(EstadoFinal.DEVOLUCION_NO_PROCESADA);
        verify(reservas).marcarCancelada(new ReservaId(RESERVA));
        List<EventoDominio> publicados = eventosPublicados(2);
        assertThat(publicados.get(0).resultado()).isEqualTo("DEVOLUCION_NO_PROCESADA");
        assertThat(publicados.get(1).tipo()).isEqualTo("DevolucionFallida");
        assertThat(publicados.get(1).resultado()).isEqualTo("FALLIDO");
        assertThat(publicados.get(1).destinatarios()).containsExactly(HUESPED);
    }

    @Test
    void aMenosDeDosDiasNoHayDevolucionNiSeLlamaALaPasarela() {
        prepararReserva(HOY.plusDays(1), EstadoReserva.CONFIRMADA, HUESPED, "Flexible");

        Cancelacion cancelacion = servicio.cancelarReserva(HUESPED, RESERVA, COMANDO);

        assertThat(cancelacion.estadoFinal()).isEqualTo(EstadoFinal.SIN_DEVOLUCION);
        assertThat(cancelacion.valorDevolucion()).isZero();
        verifyNoInteractions(pagos);
        assertThat(eventosPublicados(1).get(0).tipo()).isEqualTo("ReservaCancelada");
    }

    @Test
    void unaReservaAjenaNoSePuedeCancelar() {
        when(reservas.datosReserva(new ReservaId(RESERVA))).thenReturn(datosReserva(HOY.plusDays(10), EstadoReserva.CONFIRMADA, UUID.randomUUID()));

        assertThatThrownBy(() -> servicio.cancelarReserva(HUESPED, RESERVA, COMANDO))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.ACCESO_DENEGADO));
        verify(reservas, never()).marcarCancelada(any());
        verifyNoInteractions(pagos, eventos);
    }

    @Test
    void soloSeCancelaUnaReservaConfirmada() {
        when(reservas.datosReserva(new ReservaId(RESERVA))).thenReturn(datosReserva(HOY.plusDays(10), EstadoReserva.CANCELADA, HUESPED));

        assertThatThrownBy(() -> servicio.cancelarReserva(HUESPED, RESERVA, COMANDO))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO));
        verifyNoInteractions(pagos, eventos);
    }

    @Test
    void unaPoliticaSinReglasDefinidasRespondeConflictoSinCancelar() {
        prepararReserva(HOY.plusDays(10), EstadoReserva.CONFIRMADA, HUESPED, "Moderada");

        assertThatThrownBy(() -> servicio.cancelarReserva(HUESPED, RESERVA, COMANDO))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO));
        verify(reservas, never()).marcarCancelada(any());
        verifyNoInteractions(pagos, eventos);
    }

    private void prepararReserva(LocalDate llegada, EstadoReserva estado, UUID huesped, String politica) {
        when(reservas.datosReserva(new ReservaId(RESERVA))).thenReturn(datosReserva(llegada, estado, huesped));
        when(publicaciones.datosPublicacion(new PublicacionId(PUBLICACION))).thenReturn(
                new DatosPublicacion(PUBLICACION, ANFITRION, true, new BigDecimal("100000"), 4, politica, List.of()));
    }

    private static DatosReserva datosReserva(LocalDate llegada, EstadoReserva estado, UUID huesped) {
        return new DatosReserva(RESERVA, huesped, PUBLICACION, llegada, estado, TOTAL, TARIFA_SERVICIO);
    }

    private List<EventoDominio> eventosPublicados(int cuantos) {
        ArgumentCaptor<EventoDominio> evento = ArgumentCaptor.forClass(EventoDominio.class);
        verify(eventos, times(cuantos)).publicar(evento.capture());
        return evento.getAllValues();
    }
}
