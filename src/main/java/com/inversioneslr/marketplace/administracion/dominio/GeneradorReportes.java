package com.inversioneslr.marketplace.administracion.dominio;

public interface GeneradorReportes {

    byte[] generar(TipoReporte tipo, FormatoReporte formato);
}
