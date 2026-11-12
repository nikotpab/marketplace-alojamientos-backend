package com.inversioneslr.marketplace.cotizacion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.cotizacion.ConfiguracionTarifas;
import com.inversioneslr.marketplace.cotizacion.Cotizacion;
import com.inversioneslr.marketplace.cotizacion.aplicacion.ServicioCotizacion.Desglose;
import com.inversioneslr.marketplace.cotizacion.dominio.RepositorioTarifas;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.ServicioAdicional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** SCRUM-141: CU-16 y CU-28 sin base de datos ni Docker, con la fachada de publicaciones simulada. */
@ExtendWith(MockitoExtension.class)
class ServicioCotizacionTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate LLEGADA = LocalDate.of(2026, 12, 1);
    private static final UUID PUBLICACION = UUID.randomUUID();
    private static final UUID ADMINISTRADOR = UUID.randomUUID();
    private static final ServicioAdicional SALIDA_TARDIA = new ServicioAdicional(UUID.randomUUID(), "Salida tardía", new BigDecimal("50000"));
    /** Una LIMPIEZA sin definir en la base llega aquí en 0 (RepositorioTarifasSql, pendiente 5 del README). */
    private static final ConfiguracionTarifas TARIFAS = new ConfiguracionTarifas(BigDecimal.ZERO, new BigDecimal("10"),
            new BigDecimal("5"), new BigDecimal("10"), 7);

    @Mock
    private RepositorioTarifas tarifas;
    @Mock
    private FachadaPublicaciones publicaciones;
    @Mock
    private PublicadorEventos eventos;

    private ServicioCotizacion servicio;

    @BeforeEach
    void preparar() {
        servicio = new ServicioCotizacion(tarifas, publicaciones, eventos, RELOJ);
    }

    @Test
    void sumaNochesServicioYLimpiezaYRestaElDescuentoPromocional() {
        prepararPublicacion(new BigDecimal("100000"));
        when(tarifas.vigentes()).thenReturn(TARIFAS);

        Cotizacion cotizacion = servicio.cotizar(PUBLICACION, LLEGADA, LLEGADA.plusDays(3), List.of());

        assertThat(cotizacion.noches()).isEqualTo(3);
        assertThat(cotizacion.precioBase()).isEqualByComparingTo("300000");
        assertThat(cotizacion.tarifaLimpieza()).isEqualByComparingTo("0");
        assertThat(cotizacion.tarifaServicio()).isEqualByComparingTo("30000");
        assertThat(cotizacion.descuento()).isEqualByComparingTo("15000");
        assertThat(cotizacion.total()).isEqualByComparingTo("315000");
    }

    @Test
    void desdeLasNochesDeEstanciaLargaSumaEseDescuentoYCobraLosAdicionales() {
        prepararPublicacion(new BigDecimal("100000"));
        when(tarifas.vigentes()).thenReturn(TARIFAS);

        Cotizacion cotizacion = servicio.cotizar(PUBLICACION, LLEGADA, LLEGADA.plusDays(10), List.of(SALIDA_TARDIA.id()));

        assertThat(cotizacion.precioBase()).isEqualByComparingTo("1000000");
        assertThat(cotizacion.descuento()).isEqualByComparingTo("150000");
        assertThat(cotizacion.costoAdicionales()).isEqualByComparingTo("50000");
        assertThat(cotizacion.total()).isEqualByComparingTo("1000000");
    }

    @Test
    void laLimpiezaEsUnPorcentajeDelSubtotalYCadaValorSeRedondeaAlPeso() {
        prepararPublicacion(new BigDecimal("33333"));
        when(tarifas.vigentes()).thenReturn(new ConfiguracionTarifas(new BigDecimal("8"), new BigDecimal("10"),
                new BigDecimal("5"), new BigDecimal("10"), 7));

        Cotizacion cotizacion = servicio.cotizar(PUBLICACION, LLEGADA, LLEGADA.plusDays(1), List.of());

        assertThat(cotizacion.tarifaLimpieza()).isEqualByComparingTo("2667");
        assertThat(cotizacion.tarifaServicio()).isEqualByComparingTo("3333");
        assertThat(cotizacion.descuento()).isEqualByComparingTo("1667");
        assertThat(cotizacion.total()).isEqualByComparingTo("37666");
    }

    @Test
    void unAdicionalQueNoEsDelAlojamientoSeRechaza() {
        prepararPublicacion(new BigDecimal("100000"));
        when(tarifas.vigentes()).thenReturn(TARIFAS);

        assertThatThrownBy(() -> servicio.cotizar(PUBLICACION, LLEGADA, LLEGADA.plusDays(2), List.of(UUID.randomUUID())))
                .satisfies(datoInvalido("adicionales"));
    }

    @Test
    void elDesgloseParaElHuespedValidaFechasEstadoCapacidadYDisponibilidad() {
        assertThatThrownBy(() -> servicio.cotizarParaHuesped(PUBLICACION, LLEGADA, LLEGADA, 2, List.of()))
                .satisfies(datoInvalido("salida"));
        assertThatThrownBy(() -> servicio.cotizarParaHuesped(PUBLICACION, LocalDate.now(RELOJ).minusDays(1), LLEGADA, 2, List.of()))
                .satisfies(datoInvalido("llegada"));

        when(publicaciones.datosPublicacion(PUBLICACION)).thenReturn(publicacion(false, new BigDecimal("100000")));
        assertThatThrownBy(() -> servicio.cotizarParaHuesped(PUBLICACION, LLEGADA, LLEGADA.plusDays(2), 2, List.of()))
                .satisfies(conflicto());

        when(publicaciones.datosPublicacion(PUBLICACION)).thenReturn(publicacion(true, new BigDecimal("100000")));
        assertThatThrownBy(() -> servicio.cotizarParaHuesped(PUBLICACION, LLEGADA, LLEGADA.plusDays(2), 9, List.of()))
                .satisfies(datoInvalido("huespedes"));

        when(publicaciones.periodoDisponible(PUBLICACION, LLEGADA, LLEGADA.plusDays(2))).thenReturn(false);
        assertThatThrownBy(() -> servicio.cotizarParaHuesped(PUBLICACION, LLEGADA, LLEGADA.plusDays(2), 2, List.of()))
                .satisfies(conflicto());
        verifyNoInteractions(tarifas);
    }

    @Test
    void elDesgloseParaElHuespedIncluyeLosAdicionalesDisponibles() {
        prepararPublicacion(new BigDecimal("100000"));
        when(publicaciones.periodoDisponible(PUBLICACION, LLEGADA, LLEGADA.plusDays(3))).thenReturn(true);
        when(tarifas.vigentes()).thenReturn(TARIFAS);

        Desglose desglose = servicio.cotizarParaHuesped(PUBLICACION, LLEGADA, LLEGADA.plusDays(3), 2, List.of());

        assertThat(desglose.total()).isEqualByComparingTo("315000");
        assertThat(desglose.moneda()).isEqualTo("COP");
        assertThat(desglose.adicionalesDisponibles()).extracting(ServicioCotizacion.AdicionalDisponible::nombre)
                .containsExactly("Salida tardía");
    }

    @Test
    void actualizarTarifasGuardaYPublicaSoloLoQueCambio() {
        when(tarifas.vigentes()).thenReturn(TARIFAS);
        ConfiguracionTarifas nuevas = new ConfiguracionTarifas(BigDecimal.ZERO, new BigDecimal("12"), new BigDecimal("5"),
                new BigDecimal("10"), 7);

        servicio.actualizarTarifas(ADMINISTRADOR, nuevas);

        verify(tarifas).guardar(nuevas, ADMINISTRADOR);
        ArgumentCaptor<EventoDominio> evento = ArgumentCaptor.forClass(EventoDominio.class);
        verify(eventos).publicar(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("TarifasActualizadas");
        assertThat(evento.getValue().actorId()).isEqualTo(ADMINISTRADOR);
        assertThat(evento.getValue().detalle()).containsOnlyKeys("Tarifa de servicio")
                .containsEntry("Tarifa de servicio", "10 % → 12 %");
    }

    private void prepararPublicacion(BigDecimal precioNoche) {
        when(publicaciones.datosPublicacion(PUBLICACION)).thenReturn(publicacion(true, precioNoche));
    }

    private static DatosPublicacion publicacion(boolean activa, BigDecimal precioNoche) {
        return new DatosPublicacion(PUBLICACION, UUID.randomUUID(), activa, precioNoche, 4, "Flexible", List.of(SALIDA_TARDIA));
    }

    private static Consumer<Throwable> datoInvalido(String campo) {
        return error -> {
            assertThat(error).isInstanceOf(ExcepcionNegocio.class);
            assertThat(((ExcepcionNegocio) error).tipo()).isEqualTo(ExcepcionNegocio.Tipo.DATOS_INVALIDOS);
            assertThat(((ExcepcionNegocio) error).campos()).containsKey(campo);
        };
    }

    private static Consumer<Throwable> conflicto() {
        return error -> assertThat(((ExcepcionNegocio) error).tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO);
    }
}
