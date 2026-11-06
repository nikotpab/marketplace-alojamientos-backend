package com.inversioneslr.marketplace.compartido.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.SuscriptorAsincrono;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Transactional Outbox (ADR-06 · SEC-05, parte 2): entrega los eventos fuera del camino crítico. */
@Component
public class ProcesadorEventos {

    private final List<SuscriptorAsincrono> suscriptores;
    private final JdbcClient jdbc;

    public ProcesadorEventos(List<SuscriptorAsincrono> suscriptores, JdbcClient jdbc) {
        this.suscriptores = suscriptores;
        this.jdbc = jdbc;
    }

    /**
     * Pendiente: leer evento_pendiente, entregar a cada suscriptor y marcar entregado en la misma
     * transacción (así un reintento no duplica la notificación, RNF-14). Si falla o tarda más de 10 s,
     * el evento sigue pendiente y se reintenta con un límite.
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void procesarPendientes() {
    }
}
