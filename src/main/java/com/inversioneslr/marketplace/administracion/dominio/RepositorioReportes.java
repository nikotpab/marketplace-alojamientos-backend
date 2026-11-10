package com.inversioneslr.marketplace.administracion.dominio;

public interface RepositorioReportes {

    /** Hasta 10.000 filas (RNF-08). */
    Reporte consultar(TipoReporte tipo, FiltrosReporte filtros);
}
