package com.inversioneslr.marketplace.cotizacion.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FachadaCotizacion {

    /** CU-16: no crea reserva ni bloquea el periodo. */
    Cotizacion cotizar(UUID publicacionId, LocalDate llegada, LocalDate salida, List<UUID> serviciosAdicionales);

    ConfiguracionTarifas consultarTarifas();

    /** CU-28: aplica a las cotizaciones nuevas en máximo 1 min (RNF-29). */
    void actualizarTarifas(UUID administradorId, ConfiguracionTarifas tarifas);
}
