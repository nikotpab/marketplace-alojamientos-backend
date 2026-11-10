package com.inversioneslr.marketplace.compartido;

/** Se atiende fuera del camino crítico. Debe tolerar recibir un evento repetido. */
public interface SuscriptorAsincrono {

    void atender(EventoDominio evento);
}
