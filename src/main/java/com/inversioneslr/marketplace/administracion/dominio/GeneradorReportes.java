package com.inversioneslr.marketplace.administracion.dominio;

public interface GeneradorReportes {

    byte[] generar(Reporte reporte, FormatoReporte formato);
}
