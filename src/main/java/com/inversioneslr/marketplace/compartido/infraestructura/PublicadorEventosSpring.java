package com.inversioneslr.marketplace.compartido.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.dominio.SuscriptorAsincrono;
import com.inversioneslr.marketplace.compartido.dominio.SuscriptorSincrono;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** ADR-06: todos los suscriptores se atienden en la misma transacción del caso de uso. */
@Component
public class PublicadorEventosSpring implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventosSpring.class);

    private final List<SuscriptorSincrono> sincronos;
    private final List<SuscriptorAsincrono> asincronos;

    public PublicadorEventosSpring(List<SuscriptorSincrono> sincronos, List<SuscriptorAsincrono> asincronos) {
        this.sincronos = sincronos;
        this.asincronos = asincronos;
    }

    @Override
    @Transactional
    public void publicar(EventoDominio evento) {
        sincronos.forEach(s -> s.atender(evento));
        asincronos.forEach(s -> s.atender(evento));
        log.atInfo()
                .addKeyValue("tipoEvento", evento.tipo())
                .addKeyValue("usuario", String.valueOf(evento.actorId()))
                .addKeyValue("entidad", evento.entidad())
                .addKeyValue("entidadId", String.valueOf(evento.entidadId()))
                .addKeyValue("resultado", evento.resultado())
                .log("Evento de dominio");
    }
}
