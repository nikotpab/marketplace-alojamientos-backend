package com.inversioneslr.marketplace.cotizacion.presentacion;

import com.inversioneslr.marketplace.cotizacion.aplicacion.ServicioCotizacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    private final ServicioCotizacion cotizacion;

    public ControladorCotizacion(ServicioCotizacion cotizacion) {
        this.cotizacion = cotizacion;
    }

    @PostMapping
    public ServicioCotizacion.Desglose cotizar(@Valid @RequestBody SolicitudCotizacion solicitud) {
        return cotizacion.cotizarParaHuesped(solicitud.publicacionId(), solicitud.llegada(), solicitud.salida(),
                solicitud.huespedes(), solicitud.adicionales());
    }

    /** Con los nombres que envía el frontend. */
    public record SolicitudCotizacion(@NotNull UUID publicacionId,
                                      @NotNull LocalDate llegada,
                                      @NotNull LocalDate salida,
                                      @NotNull @Positive Integer huespedes,
                                      List<UUID> adicionales) {
    }
}
