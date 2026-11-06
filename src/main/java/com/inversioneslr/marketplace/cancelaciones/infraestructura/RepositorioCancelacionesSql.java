package com.inversioneslr.marketplace.cancelaciones.infraestructura;

import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion;
import com.inversioneslr.marketplace.cancelaciones.dominio.RepositorioCancelaciones;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioCancelacionesSql implements RepositorioCancelaciones {

    private final JdbcClient jdbc;

    public RepositorioCancelacionesSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Cancelacion guardar(Cancelacion cancelacion) {
        throw new UnsupportedOperationException("SQL de cancelacion");
    }
}
