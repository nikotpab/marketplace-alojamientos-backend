package com.inversioneslr.marketplace.cancelaciones.dominio;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RepositorioCancelaciones {

    Cancelacion guardar(Cancelacion cancelacion);

    /** Una sola consulta: las cancelaciones de esas reservas, para el historial. */
    List<Cancelacion> buscarPorReservas(Collection<UUID> reservaIds);
}
