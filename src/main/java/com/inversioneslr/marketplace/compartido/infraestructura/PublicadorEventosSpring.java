package com.inversioneslr.marketplace.compartido.infraestructura;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-06 · SEC-05 con Spring Modulith. En la transacción del caso de uso, la auditoría atiende el evento y el
 * registro de eventos (outbox) lo guarda para las notificaciones, que se atienden después de confirmar.
 */
@Component
public class PublicadorEventosSpring implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventosSpring.class);

    private final ApplicationEventPublisher publicador;

    public PublicadorEventosSpring(ApplicationEventPublisher publicador) {
        this.publicador = publicador;
    }

    /** Si el caso de uso no tiene transacción, abre una: así el evento siempre queda registrado. */
    @Override
    @Transactional
    public void publicar(EventoDominio evento) {
        publicador.publishEvent(evento);
        log.atInfo()
                .addKeyValue("tipoEvento", evento.tipo())
                .addKeyValue("usuario", String.valueOf(evento.actorId()))
                .addKeyValue("entidad", evento.entidad())
                .addKeyValue("entidadId", String.valueOf(evento.entidadId()))
                .addKeyValue("resultado", evento.resultado())
                .log("Evento de dominio");
    }
}
