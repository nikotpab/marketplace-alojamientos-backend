package com.inversioneslr.marketplace.busqueda.presentacion;

import com.inversioneslr.marketplace.busqueda.aplicacion.ServicioBusqueda;
import com.inversioneslr.marketplace.busqueda.dominio.AlojamientoResumen;
import com.inversioneslr.marketplace.busqueda.dominio.FiltrosBusqueda;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alojamientos")
public class ControladorBusqueda {

    private final ServicioBusqueda servicio;

    public ControladorBusqueda(ServicioBusqueda servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<AlojamientoResumen> buscar(FiltrosBusqueda filtros, @RequestParam(defaultValue = "0") int pagina) {
        return servicio.buscarAlojamientos(filtros, pagina);
    }

    @GetMapping("/{id}")
    public ServicioBusqueda.Detalle consultarDetalle(@PathVariable UUID id) {
        return servicio.consultarDetalle(id);
    }
}
