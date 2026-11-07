package com.inversioneslr.marketplace.compartido.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.ComprobacionEstado;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Latido cada 30 s (RNF-15). Es una línea de log que llega a CloudWatch: la alarma se dispara si falta
 * el latido correcto, tanto si la aplicación dejó de responder como si un componente reporta una falla.
 */
@Component
public class TareaLatido {

    private static final Logger log = LoggerFactory.getLogger(TareaLatido.class);

    private final List<ComprobacionEstado> comprobaciones;

    public TareaLatido(List<ComprobacionEstado> comprobaciones) {
        this.comprobaciones = comprobaciones;
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
    public void enviar() {
        Map<String, Boolean> estados = new LinkedHashMap<>();
        comprobaciones.forEach(comprobacion -> estados.put(comprobacion.componente(), disponible(comprobacion)));
        boolean correcto = !estados.containsValue(false);

        LoggingEventBuilder latido = correcto ? log.atInfo() : log.atWarn();
        estados.forEach((componente, disponible) -> latido.addKeyValue(componente, disponible ? "UP" : "DOWN"));
        latido.addKeyValue("latido", correcto ? "OK" : "FALLA").log("Latido");
    }

    private static boolean disponible(ComprobacionEstado comprobacion) {
        try {
            return comprobacion.disponible();
        } catch (RuntimeException e) {
            log.warn("No se pudo comprobar {}", comprobacion.componente(), e);
            return false;
        }
    }
}
