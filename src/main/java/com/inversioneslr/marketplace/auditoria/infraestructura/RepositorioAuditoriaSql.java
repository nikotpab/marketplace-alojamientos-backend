package com.inversioneslr.marketplace.auditoria.infraestructura;

import com.inversioneslr.marketplace.auditoria.api.FachadaAuditoria.EventoAuditoria;
import com.inversioneslr.marketplace.auditoria.api.FachadaAuditoria.FiltrosAuditoria;
import com.inversioneslr.marketplace.auditoria.dominio.RepositorioAuditoria;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioAuditoriaSql implements RepositorioAuditoria {

    private final JdbcClient jdbc;

    public RepositorioAuditoriaSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(EventoAuditoria evento) {
        throw new UnsupportedOperationException("SQL de auditoria.evento_auditoria");
    }

    @Override
    public List<EventoAuditoria> consultar(FiltrosAuditoria filtros) {
        throw new UnsupportedOperationException("SQL de auditoria.evento_auditoria");
    }
}
