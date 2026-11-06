package com.inversioneslr.marketplace.notificaciones.presentacion;

import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import com.inversioneslr.marketplace.notificaciones.aplicacion.ServicioNotificaciones;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
public class ControladorNotificaciones {

    private final ServicioNotificaciones servicio;

    public ControladorNotificaciones(ServicioNotificaciones servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ServicioNotificaciones.Bandeja consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.consultar(usuario.id());
    }

    @PostMapping("/{id}/lectura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLeida(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        servicio.marcarLeida(usuario.id(), id);
    }

    @PostMapping("/lectura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarTodasLeidas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        servicio.marcarTodasLeidas(usuario.id());
    }
}
