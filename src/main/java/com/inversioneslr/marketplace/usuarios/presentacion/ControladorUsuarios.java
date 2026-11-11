package com.inversioneslr.marketplace.usuarios.presentacion;

import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import com.inversioneslr.marketplace.usuarios.FachadaUsuarios.UsuarioResumen;
import com.inversioneslr.marketplace.usuarios.aplicacion.ServicioAutenticacion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResumen registrar(@Valid @RequestBody ServicioAutenticacion.RegistroComando comando) {
        return servicio.registrar(comando);
    }

    @PostMapping("/auth/login")
    public ServicioAutenticacion.Sesion iniciarSesion(@Valid @RequestBody ServicioAutenticacion.CredencialesComando comando) {
        return servicio.iniciarSesion(comando);
    }

    /** SCRUM-110: 202 siempre, exista o no el correo. El enlace llega por correo. */
    @PostMapping("/auth/password/recover")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void solicitarRecuperacion(@Valid @RequestBody ServicioAutenticacion.CorreoComando comando) {
        servicio.solicitarRecuperacion(comando);
    }

    @PostMapping("/auth/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecerContrasena(@Valid @RequestBody ServicioAutenticacion.RestablecerContrasenaComando comando) {
        servicio.restablecerContrasena(comando);
    }

    /** SCRUM-109: el token del enlace que llegó al registrarse. */
    @PostMapping("/auth/email/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verificarCorreo(@Valid @RequestBody ServicioAutenticacion.VerificacionCorreoComando comando) {
        servicio.verificarCorreo(comando);
    }

    /** SCRUM-109: 202 siempre, exista o no la cuenta, o si ya está verificada. */
    @PostMapping("/auth/email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarVerificacion(@Valid @RequestBody ServicioAutenticacion.CorreoComando comando) {
        servicio.reenviarVerificacion(comando);
    }

    @GetMapping("/usuarios/me")
    public ServicioAutenticacion.Perfil consultarPerfil(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.consultarPerfil(usuario.id());
    }

    @PutMapping("/usuarios/me")
    public ServicioAutenticacion.Perfil actualizarPerfil(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                         @Valid @RequestBody ServicioAutenticacion.PerfilComando comando) {
        return servicio.actualizarPerfil(usuario.id(), comando);
    }

    @PatchMapping("/usuarios/me/estado")
    public ServicioAutenticacion.Perfil cambiarEstadoCuenta(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                            @Valid @RequestBody ServicioAutenticacion.EstadoCuentaComando comando) {
        return servicio.cambiarEstadoCuenta(usuario.id(), comando);
    }
}
