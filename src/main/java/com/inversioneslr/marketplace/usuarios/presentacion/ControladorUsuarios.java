package com.inversioneslr.marketplace.usuarios.presentacion;

import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import com.inversioneslr.marketplace.usuarios.api.FachadaUsuarios.UsuarioResumen;
import com.inversioneslr.marketplace.usuarios.aplicacion.ServicioAutenticacion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ControladorUsuarios {

    private final ServicioAutenticacion servicio;

    public ControladorUsuarios(ServicioAutenticacion servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/auth/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResumen registrar(@Valid @RequestBody ServicioAutenticacion.RegistroComando comando) {
        return servicio.registrar(comando);
    }

    @PostMapping("/auth/inicio-sesion")
    public ServicioAutenticacion.Sesion iniciarSesion(@Valid @RequestBody ServicioAutenticacion.CredencialesComando comando) {
        return servicio.iniciarSesion(comando);
    }

    @PostMapping("/auth/recuperacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void solicitarRecuperacion(@Valid @RequestBody ServicioAutenticacion.SolicitudRecuperacionComando comando) {
        servicio.solicitarRecuperacion(comando);
    }

    @PostMapping("/auth/recuperacion/confirmacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmarRecuperacion(@Valid @RequestBody ServicioAutenticacion.RecuperacionComando comando) {
        servicio.confirmarRecuperacion(comando);
    }

    @GetMapping("/usuarios/me")
    public UsuarioResumen consultarPerfil(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.consultarPerfil(usuario.id());
    }

    @PutMapping("/usuarios/me")
    public UsuarioResumen actualizarPerfil(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                           @Valid @RequestBody ServicioAutenticacion.PerfilComando comando) {
        return servicio.actualizarPerfil(usuario.id(), comando);
    }

    @PutMapping("/usuarios/me/estado-cuenta")
    public UsuarioResumen cambiarEstadoCuenta(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                              @Valid @RequestBody ServicioAutenticacion.EstadoCuentaComando comando) {
        return servicio.cambiarEstadoCuenta(usuario.id(), comando);
    }
}
