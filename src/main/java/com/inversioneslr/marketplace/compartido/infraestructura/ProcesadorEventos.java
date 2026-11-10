package com.inversioneslr.marketplace.compartido.infraestructura;

import com.inversioneslr.marketplace.compartido.ComprobacionEstado;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ADR-06 · SEC-05, parte 2: reintenta las entregas fallidas del registro de eventos de Spring Modulith. Cada
 * evento se intenta hasta 5 veces; después queda FAILED en event_publication.
 */
@Component
public class ProcesadorEventos implements ComprobacionEstado {

    private static final int MAXIMO_INTENTOS = 5;
    private static final Duration ESPERA_MAXIMA_ENTRE_CICLOS = Duration.ofMinutes(1);

    private final FailedEventPublications fallidas;
    private final Clock reloj;
    private volatile Instant ultimoCiclo;

    public ProcesadorEventos(FailedEventPublications fallidas, Clock reloj) {
        this.fallidas = fallidas;
        this.reloj = reloj;
        this.ultimoCiclo = reloj.instant();
    }

    @Override
    public String componente() {
        return "procesadorEventos";
    }

    /** ADR-06: si los ciclos dejan de terminar, los reintentos están detenidos aunque la API responda. */
    @Override
    public boolean disponible() {
        return ultimoCiclo.isAfter(reloj.instant().minus(ESPERA_MAXIMA_ENTRE_CICLOS));
    }

    @Scheduled(cron = "${marketplace.eventos.cron:*/5 * * * * *}")
    public void reintentarFallidos() {
        fallidas.resubmit(ResubmissionOptions.defaults()
                .withFilter(publicacion -> publicacion.getCompletionAttempts() < MAXIMO_INTENTOS));
        ultimoCiclo = reloj.instant();
    }
}
