package com.inversioneslr.marketplace.publicaciones.presentacion;

import com.inversioneslr.marketplace.publicaciones.aplicacion.ServicioPublicaciones;
import com.inversioneslr.marketplace.publicaciones.dominio.TipoConfigurado;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/configuracion")
public class ControladorConfiguracion {

    private final ServicioPublicaciones servicio;

    public ControladorConfiguracion(ServicioPublicaciones servicio) {
        this.servicio = servicio;
    }

    /** Solo los tipos activos: los desactivados no se ofrecen al crear publicaciones. */
    @GetMapping("/tipos-alojamiento")
    public List<TipoConfigurado> listarTiposActivos() {
        return servicio.listarTiposActivos();
    }
}
