package com.inversioneslr.marketplace.cancelaciones.presentacion;

import com.inversioneslr.marketplace.cancelaciones.aplicacion.ServicioCancelaciones;
import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion;
import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservas/{reservaId}/cancelacion")
public class ControladorCancelaciones {

    private final ServicioCancelaciones servicio;

    public ControladorCancelaciones(ServicioCancelaciones servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/estimacion")
    public BigDecimal estimarDevolucion(@AuthenticationPrincipal UsuarioAutenticado huesped, @PathVariable UUID reservaId) {
        return servicio.estimarDevolucion(huesped.id(), reservaId);
    }

    @PostMapping
    public Cancelacion cancelar(@AuthenticationPrincipal UsuarioAutenticado huesped, @PathVariable UUID reservaId,
                                @Valid @RequestBody ServicioCancelaciones.CancelarReservaComando comando) {
        return servicio.cancelarReserva(huesped.id(), reservaId, comando);
    }
}
