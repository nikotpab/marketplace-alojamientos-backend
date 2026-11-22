package com.inversioneslr.marketplace.compartido;

/** Componente que informa su estado en el latido. */
public interface ComprobacionEstado {

    String componente();

    boolean disponible();
}
