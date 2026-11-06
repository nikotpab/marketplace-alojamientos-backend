package com.inversioneslr.marketplace.publicaciones.dominio;

import java.time.LocalDate;
import java.util.UUID;

public record PeriodoDisponibilidad(UUID id, UUID publicacionId, LocalDate fechaInicio, LocalDate fechaFin, Tipo tipo) {

    public enum Tipo {
        DISPONIBLE, BLOQUEADO, RESERVADO
    }
}
