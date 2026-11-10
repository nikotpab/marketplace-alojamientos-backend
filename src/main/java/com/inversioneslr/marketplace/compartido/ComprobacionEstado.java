package com.inversioneslr.marketplace.compartido;

/** Componente que informa su estado en el latido (RNF-15). */
public interface ComprobacionEstado {

    String componente();

    boolean disponible();
}
