package com.inversioneslr.marketplace.auditoria.infraestructura;

import com.inversioneslr.marketplace.auditoria.FachadaAuditoria.EventoAuditoria;
import com.inversioneslr.marketplace.auditoria.FachadaAuditoria.FiltrosAuditoria;
import com.inversioneslr.marketplace.auditoria.dominio.RegistroAuditoria;
import com.inversioneslr.marketplace.auditoria.dominio.RepositorioAuditoria;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class RepositorioAuditoriaSql implements RepositorioAuditoria {

    private static final int MAXIMO_EVENTOS = 200;

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
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.actor_id, u.nombre || ' ' || u.apellido AS actor, u.rol, e.accion, e.entidad, e.entidad_id,
                       e.resultado, e.detalle::text AS detalle, e.hash_registro, e.creado_en
                FROM auditoria.evento_auditoria e
                LEFT JOIN usuario u ON u.id = e.actor_id
                WHERE true""");
        Map<String, Object> parametros = new HashMap<>();
        filtrar(sql, parametros, "e.actor_id = :actor", "actor", filtros.actorId());
        filtrar(sql, parametros, "e.accion = :accion", "accion", filtros.accion());
        filtrar(sql, parametros, "e.entidad = :entidad", "entidad", filtros.entidad());
        filtrar(sql, parametros, "e.resultado = :resultado", "resultado", filtros.resultado());
        filtrar(sql, parametros, "e.creado_en >= CAST(:desde AS date)::timestamp AT TIME ZONE 'America/Bogota'", "desde", filtros.desde());
        filtrar(sql, parametros, "e.creado_en < (CAST(:hasta AS date) + 1)::timestamp AT TIME ZONE 'America/Bogota'", "hasta", filtros.hasta());
        sql.append(" ORDER BY e.creado_en DESC LIMIT ").append(MAXIMO_EVENTOS);
        return jdbc.sql(sql.toString()).params(parametros).query(this::aEvento).list();
    }

    private static void filtrar(StringBuilder sql, Map<String, Object> parametros, String condicion, String nombre, Object valor) {
        if (valor != null) {
            sql.append(" AND ").append(condicion);
            parametros.put(nombre, valor);
        }
    }

    private EventoAuditoria aEvento(ResultSet fila, int numero) throws SQLException {
        return new EventoAuditoria(
                fila.getObject("id", UUID.class),
                fila.getObject("actor_id", UUID.class),
                fila.getString("actor"),
                fila.getString("rol"),
                fila.getString("accion"),
                fila.getString("entidad"),
                fila.getObject("entidad_id", UUID.class),
                fila.getString("resultado"),
                json.readValue(fila.getString("detalle"), new TypeReference<Map<String, Object>>() { }),
                fila.getString("hash_registro"),
                fila.getTimestamp("creado_en").toInstant());
    }
}
