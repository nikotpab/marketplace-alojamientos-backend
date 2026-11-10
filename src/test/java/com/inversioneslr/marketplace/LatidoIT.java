package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.inversioneslr.marketplace.compartido.ComprobacionEstado;
import com.inversioneslr.marketplace.compartido.infraestructura.ComprobacionBaseDatos;
import com.inversioneslr.marketplace.compartido.infraestructura.ProcesadorEventos;
import com.inversioneslr.marketplace.compartido.infraestructura.TareaLatido;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LatidoIT extends PruebaIntegracion {

    @Autowired
    ComprobacionBaseDatos baseDeDatos;

    @Test
    void conLaBaseDeDatosDisponibleElLatidoEsCorrecto(CapturedOutput salida) {
        new TareaLatido(List.of(baseDeDatos)).enviar();

        assertThat(salida.getOut()).contains("\"baseDeDatos\":\"UP\"", "\"latido\":\"OK\"");
    }

    @Test
    void siUnComponenteNoEstaDisponibleElLatidoReportaLaFalla(CapturedOutput salida) {
        new TareaLatido(List.of(baseDeDatos, comprobacion("pasarelaPagos", false))).enviar();

        assertThat(salida.getOut()).contains("\"baseDeDatos\":\"UP\"", "\"pasarelaPagos\":\"DOWN\"", "\"latido\":\"FALLA\"");
    }

    @Test
    void siLaComprobacionFallaElComponenteCuentaComoNoDisponible(CapturedOutput salida) {
        ComprobacionEstado rota = new ComprobacionEstado() {
            @Override
            public String componente() {
                return "rota";
            }

            @Override
            public boolean disponible() {
                throw new IllegalStateException("Sin conexión");
            }
        };

        new TareaLatido(List.of(rota)).enviar();

        assertThat(salida.getOut()).contains("\"rota\":\"DOWN\"", "\"latido\":\"FALLA\"");
    }

    @Test
    void elProcesadorDeEventosFiguraCaidoSiPasaUnMinutoSinTerminarUnCiclo() {
        Instant inicio = Instant.parse("2030-01-01T00:00:00Z");
        Clock reloj = mock(Clock.class);
        when(reloj.instant()).thenReturn(inicio);
        ProcesadorEventos procesador = new ProcesadorEventos(opciones -> { }, reloj);
        assertThat(procesador.disponible()).isTrue();

        when(reloj.instant()).thenReturn(inicio.plus(Duration.ofMinutes(2)));
        assertThat(procesador.disponible()).isFalse();

        procesador.reintentarFallidos();
        assertThat(procesador.disponible()).isTrue();
    }

    private static ComprobacionEstado comprobacion(String nombre, boolean disponible) {
        return new ComprobacionEstado() {
            @Override
            public String componente() {
                return nombre;
            }

            @Override
            public boolean disponible() {
                return disponible;
            }
        };
    }
}
