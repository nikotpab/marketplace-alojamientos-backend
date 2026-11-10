package com.inversioneslr.marketplace.reservas.presentacion;

import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import com.inversioneslr.marketplace.reservas.aplicacion.ServicioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservas")
public class ControladorReservas {

    private final ServicioReservas servicio;

    public ControladorReservas(ServicioReservas servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaConfirmada confirmar(@AuthenticationPrincipal UsuarioAutenticado huesped,
                                       @RequestHeader("Idempotency-Key") String claveSolicitud,
                                       @Valid @RequestBody ServicioReservas.ConfirmarReservaComando comando) {
        return ReservaConfirmada.de(servicio.confirmarReserva(huesped.id(), comando, claveSolicitud));
    }

    @GetMapping
    public List<ServicioReservas.ReservaVista> consultarHistorial(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                                  @RequestParam(required = false) EstadoReserva estado) {
        return servicio.consultarHistorial(usuario, estado);
    }

    /** El formato que muestra el frontend al confirmar. */
    public record ReservaConfirmada(UUID id, String codigo, EstadoReserva estado, UUID publicacionId, LocalDate llegada,
                                    LocalDate salida, int huespedes, int noches, BigDecimal total) {

        static ReservaConfirmada de(Reserva reserva) {
            return new ReservaConfirmada(reserva.id(), reserva.codigo(), reserva.estado(), reserva.publicacionId(),
                    reserva.fechaLlegada(), reserva.fechaSalida(), reserva.numeroHuespedes(), reserva.noches(), reserva.total());
        }
    }
}
