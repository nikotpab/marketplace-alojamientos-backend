package com.inversioneslr.marketplace.auditoria.aplicacion;

import com.inversioneslr.marketplace.auditoria.dominio.RegistroAuditoria;
import com.inversioneslr.marketplace.auditoria.dominio.RepositorioAuditoria;
import com.inversioneslr.marketplace.compartido.ComprobacionEstado;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ASR-07: recalcula el hash de cada registro de auditoría. Uno alterado se registra como error y deja el latido en
 * FALLA, que dispara la alarma del equipo (SCRUM-57).
 */
@Component
public class VerificacionAuditoria implements ComprobacionEstado {

    private static final Logger log = LoggerFactory.getLogger(VerificacionAuditoria.class);

    private final RepositorioAuditoria auditoria;
    private volatile boolean integra = true;

    public VerificacionAuditoria(RepositorioAuditoria auditoria) {
        this.auditoria = auditoria;
    }

    @Override
    public String componente() {
        return "auditoria";
    }

    @Override
    public boolean disponible() {
        return integra;
    }

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void verificarPeriodicamente() {
        verificar();
    }

    /** @return los registros cuyo hash no coincide */
    public List<UUID> verificar() {
        List<UUID> alterados = auditoria.listarTodos().stream()
                .filter(registro -> !registro.integro())
                .map(RegistroAuditoria::id)
                .toList();
        if (!alterados.isEmpty()) {
            log.atError().addKeyValue("alterados", alterados.toString()).log("Registros de auditoría alterados");
        }
        integra = alterados.isEmpty();
        return alterados;
    }
}
