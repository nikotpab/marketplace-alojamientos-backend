package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.inversioneslr.marketplace.compartido.dominio.ComprobacionEstado;
import com.inversioneslr.marketplace.compartido.infraestructura.ComprobacionBaseDatos;
import com.inversioneslr.marketplace.compartido.infraestructura.TareaLatido;
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
