package com.inversioneslr.marketplace.compartido.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.ComprobacionEstado;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class ComprobacionBaseDatos implements ComprobacionEstado {

    private final JdbcClient jdbc;

    public ComprobacionBaseDatos(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String componente() {
        return "baseDeDatos";
    }

    @Override
    public boolean disponible() {
        return jdbc.sql("SELECT 1").query(Integer.class).single() == 1;
    }
}
