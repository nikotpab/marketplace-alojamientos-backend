package com.inversioneslr.marketplace.compartido.infraestructura;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.SuscriptorSincrono;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** ADR-06 · SEC-05: auditoría síncrona y outbox, ambas en la transacción del caso de uso. */
@Component
public class PublicadorEventosOutbox implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventosOutbox.class);

    private final List<SuscriptorSincrono> sincronos;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public PublicadorEventosOutbox(List<SuscriptorSincrono> sincronos, JdbcClient jdbc, JsonMapper json) {
        this.sincronos = sincronos;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void publicar(EventoDominio evento) {
        sincronos.forEach(suscriptor -> suscriptor.atender(evento));
        jdbc.sql("INSERT INTO evento_pendiente (tipo, payload) VALUES (:tipo, :payload::jsonb)")
                .param("tipo", evento.tipo())
                .param("payload", json.writeValueAsString(evento))
                .update();
        log.atInfo()
                .addKeyValue("tipoEvento", evento.tipo())
                .addKeyValue("usuario", String.valueOf(evento.actorId()))
                .addKeyValue("entidad", evento.entidad())
                .addKeyValue("entidadId", String.valueOf(evento.entidadId()))
                .addKeyValue("resultado", evento.resultado())
                .log("Evento de dominio");
    }
}
