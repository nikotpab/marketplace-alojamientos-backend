package com.inversioneslr.marketplace.auditoria.dominio;

import com.inversioneslr.marketplace.auditoria.FachadaAuditoria.EventoAuditoria;
import com.inversioneslr.marketplace.auditoria.FachadaAuditoria.FiltrosAuditoria;
import java.util.List;

/** Solo inserción (RNF-24): a propósito no ofrece modificar ni borrar. */
public interface RepositorioAuditoria {

    void insertar(RegistroAuditoria registro);

    List<EventoAuditoria> consultar(FiltrosAuditoria filtros);
}
