package com.inversioneslr.marketplace.busqueda.presentacion;

import com.inversioneslr.marketplace.busqueda.aplicacion.ServicioBusqueda;
import com.inversioneslr.marketplace.busqueda.dominio.FiltrosBusqueda;
import com.inversioneslr.marketplace.busqueda.dominio.PaginaAlojamientos;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alojamientos")
public class ControladorBusqueda {

    private static final String TAMANO_PAGINA = "20";

    private final ServicioBusqueda servicio;

    public ControladorBusqueda(ServicioBusqueda servicio) {
        this.servicio = servicio;
    }

    /** servicios llega separado por comas, por ejemplo servicios=WiFi,Cocina. */
    @GetMapping
    public PaginaAlojamientos buscar(FiltrosBusqueda filtros,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = TAMANO_PAGINA) int tamano) {
        return servicio.buscarAlojamientos(filtros, pagina, tamano);
    }

    @GetMapping("/{id}")
    public ServicioBusqueda.Detalle consultarDetalle(@PathVariable UUID id) {
        return servicio.consultarDetalle(id);
    }
}
