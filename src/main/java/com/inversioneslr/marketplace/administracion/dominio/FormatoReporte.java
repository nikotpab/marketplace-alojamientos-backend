package com.inversioneslr.marketplace.administracion.dominio;

public enum FormatoReporte {
    PDF("pdf"), EXCEL("xlsx");

    private final String extension;

    FormatoReporte(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }
}
