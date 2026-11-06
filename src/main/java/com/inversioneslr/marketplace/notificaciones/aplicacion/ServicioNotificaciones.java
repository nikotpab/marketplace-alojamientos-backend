package com.inversioneslr.marketplace.notificaciones.aplicacion;

import com.inversioneslr.marketplace.notificaciones.dominio.Notificacion;
import com.inversioneslr.marketplace.notificaciones.dominio.RepositorioNotificaciones;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServicioNotificaciones {

    private final RepositorioNotificaciones notificaciones;

    public ServicioNotificaciones(RepositorioNotificaciones notificaciones) {
        this.notificaciones = notificaciones;
    }

    /** CU-21: cada usuario ve solo las suyas, de la más reciente a la más antigua. */
    public List<Notificacion> consultar(UUID usuarioId) {
        throw new UnsupportedOperationException("CU-21");
    }

    public void marcarLeida(UUID usuarioId, UUID notificacionId) {
        throw new UnsupportedOperationException("CU-21");
    }
}
