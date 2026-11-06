package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.inversioneslr.marketplace.auditoria.aplicacion.ServicioAuditoria;
import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuditoriaIT extends PruebaIntegracion {

    @Autowired
    ServicioAuditoria auditoria;

    @Test
    void registraElEventoConSuHash() {
        UUID entidad = UUID.randomUUID();

        auditoria.atender(new EventoDominio("ReservaConfirmada", null, "reserva", entidad, "EXITO", Map.of("total", 100), List.of()));

        var fila = comoAplicacion.queryForMap("SELECT accion, resultado, detalle::text AS detalle, hash_registro FROM auditoria.evento_auditoria WHERE entidad_id = ?", entidad);
        assertThat(fila.get("accion")).isEqualTo("ReservaConfirmada");
        assertThat(fila.get("resultado")).isEqualTo("EXITO");
        assertThat(fila.get("detalle").toString()).contains("\"total\"");
        assertThat(fila.get("hash_registro").toString()).hasSize(64);
    }
}
