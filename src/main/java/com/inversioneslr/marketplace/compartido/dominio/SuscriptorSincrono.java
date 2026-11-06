package com.inversioneslr.marketplace.compartido.dominio;

/** Se atiende dentro de la transacción del caso de uso: si falla, la transacción se revierte. */
public interface SuscriptorSincrono {

    void atender(EventoDominio evento);
}
