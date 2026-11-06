package com.inversioneslr.marketplace.notificaciones.aplicacion;

import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
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

    /** CU-21: cada usuario ve solo las suyas. */
    public Bandeja consultar(UUID usuarioId) {
        List<Notificacion> propias = notificaciones.listarPorUsuario(usuarioId);
        long noLeidas = propias.stream().filter(notificacion -> !notificacion.leida()).count();
        return new Bandeja(noLeidas, propias);
    }

    public void marcarLeida(UUID usuarioId, UUID notificacionId) {
        Notificacion notificacion = notificaciones.buscarPorId(notificacionId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "La notificación no existe."));
        if (!notificacion.usuarioId().equals(usuarioId)) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.ACCESO_DENEGADO, "Esta notificación no es tuya.");
        }
        notificaciones.guardar(notificacion.marcarLeida());
    }

    public void marcarTodasLeidas(UUID usuarioId) {
        notificaciones.marcarTodasLeidas(usuarioId);
    }

    public record Bandeja(long noLeidas, List<Notificacion> notificaciones) {
    }
}
