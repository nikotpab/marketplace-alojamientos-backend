package com.inversioneslr.marketplace.notificaciones.dominio;

import java.util.List;
import java.util.UUID;

public interface RepositorioNotificaciones {

    List<Notificacion> listarPorUsuario(UUID usuarioId);

    void marcarLeida(UUID usuarioId, UUID notificacionId);
}
