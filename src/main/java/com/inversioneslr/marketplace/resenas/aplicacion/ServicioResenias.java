package com.inversioneslr.marketplace.resenas.aplicacion;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.resenas.EventosResenas.ResenaOcultada;
import com.inversioneslr.marketplace.resenas.EventosResenas.ResenaPublicada;
import com.inversioneslr.marketplace.resenas.FachadaResenas;
import com.inversioneslr.marketplace.resenas.dominio.RepositorioResenas;
import com.inversioneslr.marketplace.resenas.dominio.Resena;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import com.inversioneslr.marketplace.reservas.FachadaReservas;
import com.inversioneslr.marketplace.reservas.FachadaReservas.DatosReserva;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * CU-22: solo con una reserva COMPLETADA propia y una reseña por reserva. El contenido se guarda tal cual: el
     * frontend lo muestra con la interpolación de Angular, que escapa el HTML (RNF-22).
     */
    @Transactional
    public ResenaVisible publicar(UUID huespedId, UUID reservaId, PublicarResenaComando comando) {
        DatosReserva reserva = reservas.datosReserva(reservaId);
        if (!reserva.huespedId().equals(huespedId)) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.ACCESO_DENEGADO, "Esta reserva no es tuya.");
        }
        if (reserva.estado() != EstadoReserva.COMPLETADA) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "Solo puedes reseñar una estadía completada.");
        }
        Resena resena = resenas.guardar(new Resena(UUID.randomUUID(), reservaId, comando.calificacion(), comando.contenido().strip(),
                false, null));
        eventos.publicar(new ResenaPublicada(huespedId, resena.id(), reservaId, reserva.publicacionId(), resena.calificacion()));
        return resenas.buscarVisible(resena.id()).orElseThrow();
    }

    @Override
    public List<ResenaVisible> listarVisibles(UUID publicacionId) {
        return resenas.listarVisibles(publicacionId);
    }

    /** CU-26: queda en la auditoría quién la ocultó. */
    @Override
    @Transactional
    public void ocultar(UUID administradorId, UUID resenaId) {
        Resena resena = resenas.buscarPorId(resenaId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "La reseña no existe."));
        resenas.guardar(resena.ocultar(administradorId));
        eventos.publicar(new ResenaOcultada(administradorId, resenaId));
    }

    public record PublicarResenaComando(
            @NotNull @Min(value = 1, message = "La calificación mínima es 1.") @Max(value = 5, message = "La calificación máxima es 5.") Integer calificacion,
            @NotBlank(message = "Escribe tu reseña.") @Size(max = 1000, message = "La reseña admite hasta 1.000 caracteres.") String contenido) {
    }
}
