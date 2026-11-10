package com.inversioneslr.marketplace.notificaciones.aplicacion;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.notificaciones.dominio.Notificacion;
import com.inversioneslr.marketplace.notificaciones.dominio.RepositorioNotificaciones;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@Service
public class ServicioNotificaciones {

    private final RepositorioNotificaciones notificaciones;
    private final Clock reloj;

    public ServicioNotificaciones(RepositorioNotificaciones notificaciones, Clock reloj) {
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    /**
     * RF-24 · ADR-06: una notificación por destinatario. Se atiende después de confirmar el caso de uso, en otro hilo
     * y en su propia transacción. Si falla, ProcesadorEventos la reintenta.
     */
    @ApplicationModuleListener
    public void atender(EventoDominio evento) {
        evento.destinatarios().forEach(usuarioId ->
                notificaciones.guardar(Notificacion.nueva(evento.id(), usuarioId, evento.tipo(), reloj.instant())));
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
