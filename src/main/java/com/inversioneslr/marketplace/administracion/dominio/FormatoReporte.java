package com.inversioneslr.marketplace.administracion.dominio;

public enum FormatoReporte {
    PDF("pdf", "application/pdf"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String tipoContenido;

    FormatoReporte(String extension, String tipoContenido) {
        this.extension = extension;
        this.tipoContenido = tipoContenido;
    }

    public String extension() {
        return extension;
    }

    public String tipoContenido() {
        return tipoContenido;
    }
}
