package com.inversioneslr.marketplace.notificaciones.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.SuscriptorAsincrono;
import com.inversioneslr.marketplace.notificaciones.dominio.Notificacion;
import com.inversioneslr.marketplace.notificaciones.dominio.RepositorioNotificaciones;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Registra una notificación por destinatario de cada evento (RF-24) y las consulta. */
@Repository
public class AdaptadorNotificaciones implements SuscriptorAsincrono, RepositorioNotificaciones {

    private final JdbcClient jdbc;
    private final Clock reloj;

    public AdaptadorNotificaciones(JdbcClient jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public void atender(EventoDominio evento) {
        evento.destinatarios().forEach(usuarioId -> guardar(Notificacion.nueva(usuarioId, evento.tipo(), reloj.instant())));
    }

    @Override
    public void guardar(Notificacion notificacion) {
        jdbc.sql("""
                INSERT INTO notificacion (id, usuario_id, tipo, mensaje, leida, creado_en)
                VALUES (:id, :usuario, :tipo, :mensaje, :leida, :creadoEn)
                ON CONFLICT (id) DO UPDATE SET leida = EXCLUDED.leida""")
                .param("id", notificacion.id())
                .param("usuario", notificacion.usuarioId())
                .param("tipo", notificacion.tipo())
                .param("mensaje", notificacion.mensaje())
                .param("leida", notificacion.leida())
                .param("creadoEn", Timestamp.from(notificacion.creadoEn()))
                .update();
    }

    @Override
    public Optional<Notificacion> buscarPorId(UUID id) {
        throw new UnsupportedOperationException("SQL de notificacion");
    }

    @Override
    public List<Notificacion> listarPorUsuario(UUID usuarioId) {
        throw new UnsupportedOperationException("SQL de notificacion");
    }

    @Override
    public void marcarTodasLeidas(UUID usuarioId) {
        throw new UnsupportedOperationException("SQL de notificacion");
    }
}
