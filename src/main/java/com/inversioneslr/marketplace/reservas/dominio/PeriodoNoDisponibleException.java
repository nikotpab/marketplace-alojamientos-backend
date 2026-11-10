package com.inversioneslr.marketplace.reservas.dominio;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;

/** El periodo ya no está libre, sea por la verificación previa o por la restricción anti-solape (ADR-03). */
public class PeriodoNoDisponibleException extends ExcepcionNegocio {

    public PeriodoNoDisponibleException() {
        super(Tipo.CONFLICTO, "Periodo no disponible.");
    }
}
