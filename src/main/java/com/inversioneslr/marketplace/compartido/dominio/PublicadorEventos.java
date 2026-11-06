package com.inversioneslr.marketplace.compartido.dominio;

/** Observer (ADR-06). Los casos de uso publican sin conocer a los suscriptores. */
public interface PublicadorEventos {

    void publicar(EventoDominio evento);
}
