package com.inversioneslr.marketplace.cotizacion.dominio;

import com.inversioneslr.marketplace.cotizacion.ConfiguracionTarifas;
import java.util.UUID;

public interface RepositorioTarifas {

    ConfiguracionTarifas vigentes();

    void guardar(ConfiguracionTarifas tarifas, UUID modificadoPor);
}
