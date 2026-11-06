package com.inversioneslr.marketplace.notificaciones.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioNotificaciones {

    /** Inserta la notificación o actualiza si ya existe. */
    void guardar(Notificacion notificacion);

    Optional<Notificacion> buscarPorId(UUID id);

    /** De la más reciente a la más antigua. */
    List<Notificacion> listarPorUsuario(UUID usuarioId);

    void marcarTodasLeidas(UUID usuarioId);
}
