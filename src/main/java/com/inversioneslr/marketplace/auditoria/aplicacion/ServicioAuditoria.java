package com.inversioneslr.marketplace.auditoria.aplicacion;

import com.inversioneslr.marketplace.auditoria.api.FachadaAuditoria;
import com.inversioneslr.marketplace.auditoria.dominio.RepositorioAuditoria;
import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.SuscriptorSincrono;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ServicioAuditoria implements FachadaAuditoria, SuscriptorSincrono {

    private final RepositorioAuditoria auditoria;

    public ServicioAuditoria(RepositorioAuditoria auditoria) {
        this.auditoria = auditoria;
    }

    /**
     * ADR-06 · SEC-05: inserta el evento con su hash dentro de la transacción del caso de uso.
     * ADR-07: si un usuario acumula 5 denegaciones en 10 min, alertar al administrador.
     */
    @Override
    public void atender(EventoDominio evento) {
        throw new UnsupportedOperationException("ADR-06");
    }

    @Override
    public List<EventoAuditoria> consultar(FiltrosAuditoria filtros) {
        throw new UnsupportedOperationException("CU-27");
    }
}
