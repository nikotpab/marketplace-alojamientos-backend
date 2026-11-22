package com.inversioneslr.marketplace.administracion.dominio;

public interface RepositorioReportes {

    /** Hasta 10.000 filas. */
    Reporte consultar(TipoReporte tipo, FiltrosReporte filtros);
}
