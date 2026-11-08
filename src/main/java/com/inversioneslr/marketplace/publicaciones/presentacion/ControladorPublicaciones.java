package com.inversioneslr.marketplace.publicaciones.presentacion;

import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import com.inversioneslr.marketplace.publicaciones.aplicacion.ServicioPublicaciones;
import com.inversioneslr.marketplace.publicaciones.dominio.EstadoPublicacion;
import com.inversioneslr.marketplace.publicaciones.dominio.Disponibilidad;
import com.inversioneslr.marketplace.publicaciones.dominio.PublicacionVista;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    public PublicacionVista crear(@AuthenticationPrincipal UsuarioAutenticado anfitrion,
                                  @Valid @RequestBody ServicioPublicaciones.DatosPublicacionComando datos) {
        return servicio.crear(anfitrion.id(), datos);
    }

    @PutMapping("/publicaciones/{id}")
    public PublicacionVista editar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id,
                                   @Valid @RequestBody ServicioPublicaciones.DatosPublicacionComando datos) {
        return servicio.editar(usuario, id, datos);
    }

    @PostMapping("/publicaciones/{id}/solicitar-activacion")
    public PublicacionVista solicitarActivacion(@AuthenticationPrincipal UsuarioAutenticado anfitrion, @PathVariable UUID id) {
        return servicio.solicitarActivacion(anfitrion, id);
    }

    @PostMapping("/admin/publicaciones/{id}/aprobar")
    public PublicacionVista aprobar(@AuthenticationPrincipal UsuarioAutenticado administrador, @PathVariable UUID id) {
        return servicio.aprobar(administrador.id(), id);
    }

    @PostMapping("/admin/publicaciones/{id}/rechazar")
    public PublicacionVista rechazar(@AuthenticationPrincipal UsuarioAutenticado administrador, @PathVariable UUID id,
                                     @Valid @RequestBody ServicioPublicaciones.RechazoComando rechazo) {
        return servicio.rechazar(administrador.id(), id, rechazo.motivo());
    }

    @PostMapping("/publicaciones/{id}/pausar")
    public PublicacionVista pausar(@AuthenticationPrincipal UsuarioAutenticado anfitrion, @PathVariable UUID id) {
        return servicio.pausar(anfitrion, id);
    }

    @PostMapping("/publicaciones/{id}/reactivar")
    public PublicacionVista reactivar(@AuthenticationPrincipal UsuarioAutenticado anfitrion, @PathVariable UUID id) {
        return servicio.reactivar(anfitrion, id);
    }

    @GetMapping("/publicaciones/mias")
    public List<PublicacionVista> mias(@AuthenticationPrincipal UsuarioAutenticado anfitrion) {
        return servicio.listarDelAnfitrion(anfitrion.id());
    }

    @GetMapping("/publicaciones/{id}")
    public PublicacionVista consultar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        return servicio.consultar(usuario, id);
    }

    @GetMapping("/admin/publicaciones")
    public List<PublicacionVista> todas(@RequestParam(required = false) EstadoPublicacion estado) {
        return servicio.listarTodas(estado);
    }

    /** Sin sesión, {@code usuario} llega en null. */
    @GetMapping("/publicaciones/{id}/disponibilidad")
    public Disponibilidad disponibilidad(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id,
                                         @RequestParam LocalDate desde, @RequestParam LocalDate hasta) {
        return servicio.consultarDisponibilidad(usuario, id, desde, hasta);
    }

    @PutMapping("/publicaciones/{id}/disponibilidad")
    public Disponibilidad cambiarDisponibilidad(@AuthenticationPrincipal UsuarioAutenticado anfitrion, @PathVariable UUID id,
                                                @Valid @RequestBody ServicioPublicaciones.CambioDisponibilidad cambio) {
        return servicio.cambiarDisponibilidad(anfitrion, id, cambio);
    }

    @GetMapping("/catalogos")
    public ServicioPublicaciones.Catalogos consultarCatalogos() {
        return servicio.consultarCatalogos();
    }
}
