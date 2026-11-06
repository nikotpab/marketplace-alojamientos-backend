package com.inversioneslr.marketplace.auditoria.infraestructura;

import com.inversioneslr.marketplace.auditoria.api.FachadaAuditoria.EventoAuditoria;
import com.inversioneslr.marketplace.auditoria.api.FachadaAuditoria.FiltrosAuditoria;
import com.inversioneslr.marketplace.auditoria.dominio.RegistroAuditoria;
import com.inversioneslr.marketplace.auditoria.dominio.RepositorioAuditoria;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class RepositorioAuditoriaSql implements RepositorioAuditoria {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public RepositorioAuditoriaSql(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insertar(RegistroAuditoria registro) {
        jdbc.sql("""
                INSERT INTO auditoria.evento_auditoria
                    (id, actor_id, accion, entidad, entidad_id, resultado, detalle, hash_registro, creado_en)
                VALUES (:id, :actor, :accion, :entidad, :entidadId, :resultado, :detalle::jsonb, :hash, :fecha)""")
                .param("id", registro.id())
                .param("actor", registro.actorId())
                .param("accion", registro.accion())
                .param("entidad", registro.entidad())
                .param("entidadId", registro.entidadId())
                .param("resultado", registro.resultado())
                .param("detalle", json.writeValueAsString(registro.detalle()))
                .param("hash", registro.hash())
                .param("fecha", Timestamp.from(registro.fecha()))
                .update();
    }

    @Override
    public List<EventoAuditoria> consultar(FiltrosAuditoria filtros) {
        throw new UnsupportedOperationException("SQL de auditoria.evento_auditoria");
    }
}
