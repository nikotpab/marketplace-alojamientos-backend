package com.inversioneslr.marketplace.cotizacion.infraestructura;

import com.inversioneslr.marketplace.cotizacion.api.ConfiguracionTarifas;
import com.inversioneslr.marketplace.cotizacion.dominio.RepositorioTarifas;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioTarifasSql implements RepositorioTarifas {

    private final JdbcClient jdbc;

    public RepositorioTarifasSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ConfiguracionTarifas vigentes() {
        throw new UnsupportedOperationException("SQL de configuracion_tarifa");
    }

    @Override
    public void guardar(ConfiguracionTarifas tarifas, UUID modificadoPor) {
        throw new UnsupportedOperationException("SQL de configuracion_tarifa");
    }
}
