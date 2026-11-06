package com.inversioneslr.marketplace.resenas.presentacion;

import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import com.inversioneslr.marketplace.resenas.api.FachadaResenas.ResenaVisible;
import com.inversioneslr.marketplace.resenas.aplicacion.ServicioResenias;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ControladorResenas {

    private final ServicioResenias servicio;

    public ControladorResenas(ServicioResenias servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/reservas/{reservaId}/resena")
    @ResponseStatus(HttpStatus.CREATED)
    public ResenaVisible publicar(@AuthenticationPrincipal UsuarioAutenticado huesped, @PathVariable UUID reservaId,
                                  @Valid @RequestBody ServicioResenias.PublicarResenaComando comando) {
        return servicio.publicar(huesped.id(), reservaId, comando);
    }

    @GetMapping("/publicaciones/{publicacionId}/resenas")
    public List<ResenaVisible> consultar(@PathVariable UUID publicacionId) {
        return servicio.listarVisibles(publicacionId);
    }
}
