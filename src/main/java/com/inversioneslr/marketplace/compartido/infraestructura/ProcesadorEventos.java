package com.inversioneslr.marketplace.compartido.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.SuscriptorAsincrono;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Transactional Outbox (ADR-06 · SEC-05, parte 2): entrega los eventos fuera del camino crítico. */
@Component
public class ProcesadorEventos {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorEventos.class);

    private static final int EVENTOS_POR_CICLO = 100;
    private static final int MAXIMO_INTENTOS = 5;
    private static final int TIEMPO_MAX_SEGUNDOS = 10;

    private final List<SuscriptorAsincrono> suscriptores;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final TransactionTemplate transaccion;

    public ProcesadorEventos(List<SuscriptorAsincrono> suscriptores, JdbcClient jdbc, JsonMapper json,
                             PlatformTransactionManager transacciones) {
        this.suscriptores = suscriptores;
        this.jdbc = jdbc;
        this.json = json;
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setTimeout(TIEMPO_MAX_SEGUNDOS);
    }

    @Scheduled(cron = "${marketplace.eventos.cron:*/5 * * * * *}")
    public void procesarPendientes() {
        jdbc.sql("SELECT id FROM evento_pendiente WHERE estado = 'PENDIENTE' ORDER BY creado_en LIMIT :limite")
                .param("limite", EVENTOS_POR_CICLO)
                .query(UUID.class)
                .list()
                .forEach(this::procesar);
    }

    private void procesar(UUID eventoId) {
        try {
            transaccion.executeWithoutResult(estado -> entregar(eventoId));
        } catch (RuntimeException e) {
            log.warn("No se pudo entregar el evento {}", eventoId, e);
            registrarFallo(eventoId);
        }
    }

    /** Entrega y marca en la misma transacción: un reintento no puede duplicar lo ya entregado (RNF-14). */
    private void entregar(UUID eventoId) {
        jdbc.sql("SELECT payload::text FROM evento_pendiente WHERE id = :id AND estado = 'PENDIENTE' FOR UPDATE SKIP LOCKED")
                .param("id", eventoId)
                .query(String.class)
                .optional()
                .ifPresent(payload -> {
                    EventoDominio evento = json.readValue(payload, EventoDominio.class);
                    suscriptores.forEach(suscriptor -> suscriptor.atender(evento));
                    jdbc.sql("UPDATE evento_pendiente SET estado = 'ENTREGADO', procesado_en = now() WHERE id = :id")
                            .param("id", eventoId)
                            .update();
                });
    }

    private void registrarFallo(UUID eventoId) {
        jdbc.sql("""
                UPDATE evento_pendiente
                SET intentos = intentos + 1,
                    estado = CASE WHEN intentos + 1 >= :maximo THEN 'FALLIDO' ELSE estado END
                WHERE id = :id""")
                .param("maximo", MAXIMO_INTENTOS)
                .param("id", eventoId)
                .update();
    }
}
