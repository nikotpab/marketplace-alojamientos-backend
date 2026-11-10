package com.inversioneslr.marketplace.administracion.dominio;

import java.time.LocalDate;

public interface RepositorioIndicadores {

    /** @param desde y hasta incluidos, según la fecha en que se hizo cada reserva */
    Indicadores calcular(LocalDate desde, LocalDate hasta);
}
