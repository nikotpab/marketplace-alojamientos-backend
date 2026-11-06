package com.inversioneslr.marketplace.auditoria.aplicacion;

import com.inversioneslr.marketplace.auditoria.api.FachadaAuditoria;
import com.inversioneslr.marketplace.auditoria.dominio.RegistroAuditoria;
import com.inversioneslr.marketplace.auditoria.dominio.RepositorioAuditoria;
import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.SuscriptorSincrono;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ServicioAuditoria implements FachadaAuditoria, SuscriptorSincrono {

    private final RepositorioAuditoria auditoria;
    private final Clock reloj;

    public ServicioAuditoria(RepositorioAuditoria auditoria, Clock reloj) {
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * ADR-06 · SEC-05: se ejecuta dentro de la transacción del caso de uso. Si falla, no hay cambio sin registro.
     * Pendiente ADR-07: alertar al administrador si un usuario acumula 5 denegaciones en 10 min.
     */
    @Override
    public void atender(EventoDominio evento) {
        auditoria.insertar(RegistroAuditoria.de(evento, reloj.instant()));
    }

    @Override
    public List<EventoAuditoria> consultar(FiltrosAuditoria filtros) {
        throw new UnsupportedOperationException("CU-27");
    }
}
