package com.inversioneslr.marketplace.reservas.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicacionId;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.ReservaId;
import com.inversioneslr.marketplace.cotizacion.Cotizacion;
import com.inversioneslr.marketplace.cotizacion.FachadaCotizacion;
import com.inversioneslr.marketplace.pagos.FachadaPagos;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import com.inversioneslr.marketplace.reservas.aplicacion.ServicioReservas.ConfirmarReservaComando;
import com.inversioneslr.marketplace.reservas.dominio.PeriodoNoDisponibleException;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import com.inversioneslr.marketplace.reservas.dominio.SolicitudProcesada;
import com.inversioneslr.marketplace.usuarios.FachadaUsuarios;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * SCRUM-141: CU-17 sin base de datos ni Docker. Los otros módulos se simulan por sus fachadas, así que cada prueba
 * dice exactamente qué rama de la confirmación se rompió.
 */
@ExtendWith(MockitoExtension.class)
class ServicioReservasTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final UUID HUESPED = UUID.randomUUID();
    private static final UUID ANFITRION = UUID.randomUUID();
    private static final UUID PUBLICACION = UUID.randomUUID();
    private static final String CLAVE = "clave-de-la-solicitud";
    private static final String REFERENCIA = "pi_prueba";
    private static final LocalDate LLEGADA = LocalDate.of(2026, 12, 1);
    private static final LocalDate SALIDA = LocalDate.of(2026, 12, 4);
    private static final BigDecimal TOTAL = new BigDecimal("315000");
    private static final ConfirmarReservaComando COMANDO = new ConfirmarReservaComando(PUBLICACION, LLEGADA, SALIDA, 2, List.of());

    @Mock
    private RepositorioReservas reservas;
    @Mock
    private FachadaPublicaciones publicaciones;
    @Mock
    private FachadaCotizacion cotizacion;
    @Mock
    private FachadaPagos pagos;
    @Mock
    private FachadaUsuarios usuarios;
    @Mock
    private PublicadorEventos eventos;
    @Mock
    private PlatformTransactionManager transacciones;

    private final JsonMapper json = JsonMapper.builder().build();
    private ServicioReservas servicio;

    @BeforeEach
    void preparar() {
        servicio = new ServicioReservas(reservas, publicaciones, cotizacion, pagos, usuarios, eventos,
                new TransactionTemplate(transacciones), RELOJ, json);
    }

    @Test
    void unReintentoConUnaClaveYaProcesadaDevuelveElResultadoOriginalSinCobrarDeNuevo() {
        Reserva original = reservaConfirmada("LR-2026-00001");
        when(reservas.buscarSolicitud(CLAVE)).thenReturn(Optional.of(
                new SolicitudProcesada(CLAVE, HUESPED, original.id(), json.writeValueAsString(original), RELOJ.instant())));

        Reserva resultado = servicio.confirmarReserva(HUESPED, COMANDO, CLAVE);

        assertThat(resultado).isEqualTo(original);
        verifyNoInteractions(publicaciones, cotizacion, pagos, eventos);
    }

    @Test
    void unaClaveDeOtroHuespedSeRechazaConAccesoDenegado() {
        when(reservas.buscarSolicitud(CLAVE)).thenReturn(Optional.of(
                new SolicitudProcesada(CLAVE, UUID.randomUUID(), UUID.randomUUID(), "{}", RELOJ.instant())));

        assertThatThrownBy(() -> servicio.confirmarReserva(HUESPED, COMANDO, CLAVE))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.ACCESO_DENEGADO));
        verifyNoInteractions(pagos, eventos);
    }

    @Test
    void unPeriodoNoDisponibleSeRechazaSinCotizarNiCobrar() {
        when(reservas.buscarSolicitud(CLAVE)).thenReturn(Optional.empty());
        when(publicaciones.datosPublicacion(new PublicacionId(PUBLICACION))).thenReturn(publicacionActiva());
        when(publicaciones.periodoDisponible(new PublicacionId(PUBLICACION), LLEGADA, SALIDA)).thenReturn(false);

        assertThatThrownBy(() -> servicio.confirmarReserva(HUESPED, COMANDO, CLAVE))
                .isInstanceOf(PeriodoNoDisponibleException.class);
        verifyNoInteractions(cotizacion, pagos, eventos);
    }

    @Test
    void unPagoRechazadoNoCreaLaReservaYPublicaPagoFallido() {
        prepararPeriodoDisponible();
        when(pagos.cobrar(TOTAL, CLAVE)).thenThrow(new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_RECHAZADO, "Pago rechazado."));

        assertThatThrownBy(() -> servicio.confirmarReserva(HUESPED, COMANDO, CLAVE))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.PAGO_RECHAZADO));

        EventoDominio evento = unicoEventoPublicado();
        assertThat(evento.tipo()).isEqualTo("PagoFallido");
        assertThat(evento.resultado()).isEqualTo("PAGO_RECHAZADO");
        assertThat(evento.entidadId()).isEqualTo(PUBLICACION);
        assertThat(evento.detalle()).containsEntry("total", TOTAL);
        verify(reservas, never()).guardar(any(), anyList());
        verify(pagos, never()).registrarPago(any(), any(), any(), any());
    }

    @Test
    void siOtraReservaGanaElPeriodoDevuelveElCobroYRecuerdaElRechazo() {
        prepararPeriodoDisponible();
        when(pagos.cobrar(TOTAL, CLAVE)).thenReturn(REFERENCIA);
        when(reservas.registrarSolicitud(eq(CLAVE), eq(HUESPED), any())).thenReturn(true);
        when(reservas.guardar(any(), anyList())).thenThrow(new PeriodoNoDisponibleException());

        assertThatThrownBy(() -> servicio.confirmarReserva(HUESPED, COMANDO, CLAVE))
                .isInstanceOf(PeriodoNoDisponibleException.class);

        verify(pagos).devolverCobroSinReserva(CLAVE, REFERENCIA, TOTAL);
        verify(reservas).completarSolicitud(CLAVE, null, "{\"rechazo\":\"PERIODO_NO_DISPONIBLE\"}");
        verify(pagos, never()).registrarPago(any(), any(), any(), any());
        verifyNoInteractions(eventos);
    }

    @Test
    void conElPagoAprobadoGuardaLaReservaElPagoLaClaveYPublicaReservaConfirmada() {
        prepararPeriodoDisponible();
        when(pagos.cobrar(TOTAL, CLAVE)).thenReturn(REFERENCIA);
        when(reservas.registrarSolicitud(eq(CLAVE), eq(HUESPED), any())).thenReturn(true);
        when(reservas.guardar(any(), anyList())).thenAnswer(llamada -> llamada.<Reserva>getArgument(0).conCodigo("LR-2026-00042"));

        Reserva reserva = servicio.confirmarReserva(HUESPED, COMANDO, CLAVE);

        assertThat(reserva.estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(reserva.codigo()).isEqualTo("LR-2026-00042");
        assertThat(reserva.total()).isEqualByComparingTo(TOTAL);
        assertThat(reserva.huespedId()).isEqualTo(HUESPED);
        verify(pagos).registrarPago(new ReservaId(reserva.id()), REFERENCIA, CLAVE, TOTAL);
        verify(reservas).completarSolicitud(CLAVE, reserva.id(), json.writeValueAsString(reserva));

        EventoDominio evento = unicoEventoPublicado();
        assertThat(evento.tipo()).isEqualTo("ReservaConfirmada");
        assertThat(evento.resultado()).isEqualTo(EventoDominio.EXITO);
        assertThat(evento.entidadId()).isEqualTo(reserva.id());
        assertThat(evento.destinatarios()).containsExactly(HUESPED, ANFITRION);
        assertThat(evento.detalle()).containsEntry("codigo", "LR-2026-00042").containsEntry("total", TOTAL);
    }

    private void prepararPeriodoDisponible() {
        when(reservas.buscarSolicitud(CLAVE)).thenReturn(Optional.empty());
        when(publicaciones.datosPublicacion(new PublicacionId(PUBLICACION))).thenReturn(publicacionActiva());
        when(publicaciones.periodoDisponible(new PublicacionId(PUBLICACION), LLEGADA, SALIDA)).thenReturn(true);
        when(cotizacion.cotizar(new PublicacionId(PUBLICACION), LLEGADA, SALIDA, List.of())).thenReturn(new Cotizacion(3, new BigDecimal("300000"),
                BigDecimal.ZERO, new BigDecimal("30000"), new BigDecimal("15000"), BigDecimal.ZERO, TOTAL));
    }

    private EventoDominio unicoEventoPublicado() {
        ArgumentCaptor<EventoDominio> evento = ArgumentCaptor.forClass(EventoDominio.class);
        verify(eventos).publicar(evento.capture());
        return evento.getValue();
    }

    private static DatosPublicacion publicacionActiva() {
        return new DatosPublicacion(PUBLICACION, ANFITRION, true, new BigDecimal("100000"), 4, "Flexible", List.of());
    }

    private static Reserva reservaConfirmada(String codigo) {
        return new Reserva(UUID.randomUUID(), codigo, HUESPED, PUBLICACION, LLEGADA, SALIDA, 2, EstadoReserva.CONFIRMADA, 3,
                new BigDecimal("300000"), BigDecimal.ZERO, new BigDecimal("30000"), new BigDecimal("15000"), TOTAL);
    }
}
