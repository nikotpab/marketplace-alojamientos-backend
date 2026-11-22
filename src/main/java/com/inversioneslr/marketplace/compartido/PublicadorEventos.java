package com.inversioneslr.marketplace.compartido;

/** Observer. Los casos de uso publican sin conocer a los suscriptores. */
public interface PublicadorEventos {

    void publicar(EventoDominio evento);
}
