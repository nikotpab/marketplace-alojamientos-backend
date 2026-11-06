package com.inversioneslr.marketplace.cotizacion.presentacion;

import com.inversioneslr.marketplace.cotizacion.api.Cotizacion;
import com.inversioneslr.marketplace.cotizacion.api.FachadaCotizacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cotizaciones")
public class ControladorCotizacion {

    private final FachadaCotizacion cotizacion;

    public ControladorCotizacion(FachadaCotizacion cotizacion) {
        this.cotizacion = cotizacion;
    }

    @PostMapping
    public Cotizacion cotizar(@Valid @RequestBody SolicitudCotizacion solicitud) {
        return cotizacion.cotizar(solicitud.publicacionId(), solicitud.fechaLlegada(), solicitud.fechaSalida(),
                solicitud.serviciosAdicionales());
    }

    public record SolicitudCotizacion(@NotNull UUID publicacionId,
                                      @NotNull LocalDate fechaLlegada,
                                      @NotNull LocalDate fechaSalida,
                                      List<UUID> serviciosAdicionales) {
    }
}
