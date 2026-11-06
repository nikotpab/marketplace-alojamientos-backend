package com.inversioneslr.marketplace.administracion.infraestructura;

import com.inversioneslr.marketplace.administracion.dominio.Indicadores;
import com.inversioneslr.marketplace.administracion.dominio.RepositorioIndicadores;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioIndicadoresSql implements RepositorioIndicadores {

    private final JdbcClient jdbc;

    public RepositorioIndicadoresSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Indicadores calcular() {
        throw new UnsupportedOperationException("SQL del dashboard");
    }
}
