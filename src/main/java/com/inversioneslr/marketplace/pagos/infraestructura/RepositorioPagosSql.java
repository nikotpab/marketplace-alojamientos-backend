package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.pagos.dominio.Pago;
import com.inversioneslr.marketplace.pagos.dominio.RepositorioPagos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioPagosSql implements RepositorioPagos {

    private final JdbcClient jdbc;

    public RepositorioPagosSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(Pago pago) {
        throw new UnsupportedOperationException("SQL de pago");
    }
}
