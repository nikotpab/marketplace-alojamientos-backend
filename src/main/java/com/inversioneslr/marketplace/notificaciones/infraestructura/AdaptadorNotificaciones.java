package com.inversioneslr.marketplace.notificaciones.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.SuscriptorAsincrono;
import com.inversioneslr.marketplace.notificaciones.dominio.Notificacion;
import com.inversioneslr.marketplace.notificaciones.dominio.RepositorioNotificaciones;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Registra una notificación por destinatario de cada evento (RF-24) y las consulta. */
@Repository
public class AdaptadorNotificaciones implements SuscriptorAsincrono, RepositorioNotificaciones {

    private final JdbcClient jdbc;

    public AdaptadorNotificaciones(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void atender(EventoDominio evento) {
        throw new UnsupportedOperationException("SQL de notificacion");
    }

    @Override
    public List<Notificacion> listarPorUsuario(UUID usuarioId) {
        throw new UnsupportedOperationException("SQL de notificacion");
    }

    @Override
    public void marcarLeida(UUID usuarioId, UUID notificacionId) {
        throw new UnsupportedOperationException("SQL de notificacion");
    }
}
