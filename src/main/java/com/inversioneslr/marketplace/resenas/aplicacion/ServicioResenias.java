package com.inversioneslr.marketplace.resenas.aplicacion;

import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.resenas.api.FachadaResenas;
import com.inversioneslr.marketplace.resenas.dominio.RepositorioResenas;
import com.inversioneslr.marketplace.reservas.api.FachadaReservas;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServicioResenias implements FachadaResenas {

    private final RepositorioResenas resenas;
    private final FachadaReservas reservas;
    private final PublicadorEventos eventos;

    public ServicioResenias(RepositorioResenas resenas, FachadaReservas reservas, PublicadorEventos eventos) {
        this.resenas = resenas;
        this.reservas = reservas;
        this.eventos = eventos;
    }

    /** CU-22: solo con una reserva COMPLETADA propia y una reseña por reserva. */
    public ResenaVisible publicar(UUID huespedId, UUID reservaId, PublicarResenaComando comando) {
        throw new UnsupportedOperationException("CU-22");
    }

    @Override
    public List<ResenaVisible> listarVisibles(UUID publicacionId) {
        throw new UnsupportedOperationException("CU-23");
    }

    @Override
    public void ocultar(UUID administradorId, UUID resenaId) {
        throw new UnsupportedOperationException("CU-26");
    }

    public record PublicarResenaComando(
            @NotNull @Min(value = 1, message = "La calificación mínima es 1.") @Max(value = 5, message = "La calificación máxima es 5.") Integer calificacion,
            @NotBlank String contenido) {
    }
}
