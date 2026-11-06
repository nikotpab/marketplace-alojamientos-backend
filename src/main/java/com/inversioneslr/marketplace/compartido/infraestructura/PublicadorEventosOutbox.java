package com.inversioneslr.marketplace.compartido.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.dominio.SuscriptorSincrono;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** ADR-06 · SEC-05: auditoría síncrona y outbox, ambas en la transacción del caso de uso. */
@Component
public class PublicadorEventosOutbox implements PublicadorEventos {

    private final List<SuscriptorSincrono> sincronos;
    private final JdbcClient jdbc;

    public PublicadorEventosOutbox(List<SuscriptorSincrono> sincronos, JdbcClient jdbc) {
        this.sincronos = sincronos;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void publicar(EventoDominio evento) {
        sincronos.forEach(suscriptor -> suscriptor.atender(evento));
        guardarPendiente(evento);
    }

    private void guardarPendiente(EventoDominio evento) {
        throw new UnsupportedOperationException("ADR-06: insertar en evento_pendiente");
    }
}
