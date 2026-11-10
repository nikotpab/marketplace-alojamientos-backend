package com.inversioneslr.marketplace.auditoria.aplicacion;

import com.inversioneslr.marketplace.auditoria.FachadaAuditoria;
import com.inversioneslr.marketplace.auditoria.dominio.RegistroAuditoria;
import com.inversioneslr.marketplace.auditoria.dominio.RepositorioAuditoria;
import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ServicioAuditoria implements FachadaAuditoria {

    private final RepositorioAuditoria auditoria;
    private final Clock reloj;

    public ServicioAuditoria(RepositorioAuditoria auditoria, Clock reloj) {
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * ADR-06 · SEC-05: se ejecuta al publicar el evento, dentro de la transacción del caso de uso. Si falla, el caso
     * de uso se revierte: no hay cambio sin registro.
     * Pendiente ADR-07: alertar al administrador si un usuario acumula 5 denegaciones en 10 min.
     */
    @EventListener
    public void atender(EventoDominio evento) {
        auditoria.insertar(RegistroAuditoria.de(evento, reloj.instant()));
    }

    @Override
    public List<EventoAuditoria> consultar(FiltrosAuditoria filtros) {
        if (filtros.desde() != null && filtros.hasta() != null && filtros.desde().isAfter(filtros.hasta())) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, "El rango de fechas no es válido.",
                    Map.of("desde", "La fecha inicial es posterior a la final. Elige una fecha igual o anterior."));
        }
        return auditoria.consultar(filtros);
    }
}
