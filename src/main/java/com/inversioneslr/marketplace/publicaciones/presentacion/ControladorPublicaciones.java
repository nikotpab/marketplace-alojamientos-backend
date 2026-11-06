package com.inversioneslr.marketplace.publicaciones.presentacion;

import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import com.inversioneslr.marketplace.publicaciones.aplicacion.ServicioPublicaciones;
import com.inversioneslr.marketplace.publicaciones.dominio.PeriodoDisponibilidad;
import com.inversioneslr.marketplace.publicaciones.dominio.Publicacion;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ControladorPublicaciones {

    private final ServicioPublicaciones servicio;

    public ControladorPublicaciones(ServicioPublicaciones servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/publicaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public Publicacion crear(@AuthenticationPrincipal UsuarioAutenticado anfitrion,
                             @Valid @RequestBody ServicioPublicaciones.PublicacionComando comando) {
        return servicio.crear(anfitrion.id(), comando);
    }

    @PutMapping("/publicaciones/{id}")
    public Publicacion editar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id,
                              @Valid @RequestBody ServicioPublicaciones.PublicacionComando comando) {
        return servicio.editar(usuario, id, comando);
    }

    @PostMapping("/publicaciones/{id}/solicitud-activacion")
    public Publicacion solicitarActivacion(@AuthenticationPrincipal UsuarioAutenticado anfitrion, @PathVariable UUID id) {
        return servicio.solicitarActivacion(anfitrion.id(), id);
    }

    @PostMapping("/publicaciones/{id}/revision")
    public Publicacion revisar(@AuthenticationPrincipal UsuarioAutenticado administrador, @PathVariable UUID id,
                               @Valid @RequestBody ServicioPublicaciones.RevisionComando comando) {
        return servicio.revisar(administrador.id(), id, comando);
    }

    @PostMapping("/publicaciones/{id}/pausa")
    public Publicacion pausar(@AuthenticationPrincipal UsuarioAutenticado anfitrion, @PathVariable UUID id) {
        return servicio.pausar(anfitrion.id(), id);
    }

    @GetMapping("/publicaciones")
    public List<Publicacion> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.consultar(usuario);
    }

    @PostMapping("/publicaciones/{id}/periodos")
    @ResponseStatus(HttpStatus.CREATED)
    public PeriodoDisponibilidad definirPeriodo(@AuthenticationPrincipal UsuarioAutenticado anfitrion, @PathVariable UUID id,
                                                @Valid @RequestBody ServicioPublicaciones.PeriodoComando comando) {
        return servicio.definirPeriodo(anfitrion.id(), id, comando);
    }

    @GetMapping("/publicaciones/{id}/calendario")
    public List<PeriodoDisponibilidad> consultarCalendario(@PathVariable UUID id) {
        return servicio.consultarCalendario(id);
    }

    @GetMapping("/catalogos")
    public ServicioPublicaciones.Catalogos consultarCatalogos() {
        return servicio.consultarCatalogos();
    }
}
