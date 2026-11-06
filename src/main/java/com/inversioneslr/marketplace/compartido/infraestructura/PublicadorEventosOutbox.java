package com.inversioneslr.marketplace.compartido.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.dominio.SuscriptorSincrono;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** ADR-06 · SEC-05: auditoría síncrona y outbox, ambas en la transacción del caso de uso. */
@Component
public class PublicadorEventosOutbox implements PublicadorEventos {

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
    }
}
