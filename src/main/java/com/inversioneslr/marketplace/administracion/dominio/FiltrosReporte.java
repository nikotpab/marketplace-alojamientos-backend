package com.inversioneslr.marketplace.administracion.dominio;

import java.time.LocalDate;

/** RF-34: todos opcionales. El periodo usa la fecha de creación y la ciudad no aplica al reporte de usuarios. */
public record FiltrosReporte(LocalDate desde, LocalDate hasta, String ciudad) {
}
