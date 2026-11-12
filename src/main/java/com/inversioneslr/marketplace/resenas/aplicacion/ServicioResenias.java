package com.inversioneslr.marketplace.resenas.aplicacion;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.resenas.FachadaResenas;
import com.inversioneslr.marketplace.resenas.dominio.RepositorioResenas;
import com.inversioneslr.marketplace.resenas.dominio.Resena;
import com.inversioneslr.marketplace.resenas.dominio.ResenaGuardada;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import com.inversioneslr.marketplace.reservas.FachadaReservas;
import com.inversioneslr.marketplace.reservas.FachadaReservas.DatosReserva;
import com.inversioneslr.marketplace.usuarios.FachadaUsuarios;
import com.inversioneslr.marketplace.usuarios.FachadaUsuarios.Persona;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicioResenias implements FachadaResenas {

    private final RepositorioResenas resenas;
    private final FachadaReservas reservas;
    private final FachadaUsuarios usuarios;
    private final PublicadorEventos eventos;

    public ServicioResenias(RepositorioResenas resenas, FachadaReservas reservas, FachadaUsuarios usuarios, PublicadorEventos eventos) {
        this.resenas = resenas;
        this.reservas = reservas;
        this.usuarios = usuarios;
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
        eventos.publicar(new EventoDominio("ResenaPublicada", huespedId, "resena", resena.id(), EventoDominio.EXITO,
                Map.of("reservaId", reservaId, "publicacionId", reserva.publicacionId(), "calificacion", resena.calificacion()), List.of()));
        ResenaGuardada guardada = resenas.buscarVisible(resena.id()).orElseThrow();
        return visible(guardada, usuarios.personas(Set.of(huespedId)).get(huespedId));
    }

    /** SCRUM-142: el huésped de cada reserva y su nombre en una consulta a cada fachada, sin leer sus tablas. */
    @Override
    public List<ResenaVisible> listarVisibles(UUID publicacionId) {
        Map<UUID, UUID> huespedes = reservas.huespedPorReserva(publicacionId);
        List<ResenaGuardada> guardadas = resenas.listarVisibles(huespedes.keySet());
        Map<UUID, Persona> autores = usuarios.personas(guardadas.stream().map(guardada -> huespedes.get(guardada.reservaId())).toList());
        return guardadas.stream()
                .filter(guardada -> autores.containsKey(huespedes.get(guardada.reservaId())))
                .map(guardada -> visible(guardada, autores.get(huespedes.get(guardada.reservaId()))))
                .toList();
    }

    private static ResenaVisible visible(ResenaGuardada guardada, Persona huesped) {
        return new ResenaVisible(guardada.id(), autor(huesped), guardada.calificacion(), guardada.contenido(), guardada.creadoEn());
    }

    /**
     * El nombre y la inicial del apellido, para no exponer los datos del huésped. Como el antiguo
     * concat_ws(' ', nombre, left(apellido, 1) || '.'): omite lo que falte.
     */
    private static String autor(Persona huesped) {
        String inicial = huesped.apellido() == null ? null
                : huesped.apellido().substring(0, huesped.apellido().isEmpty() ? 0 : huesped.apellido().offsetByCodePoints(0, 1)) + ".";
        return Stream.of(huesped.nombre(), inicial).filter(Objects::nonNull).collect(Collectors.joining(" "));
    }

    /** CU-26: queda en la auditoría quién la ocultó. */
    @Override
    @Transactional
    public void ocultar(UUID administradorId, UUID resenaId) {
        Resena resena = resenas.buscarPorId(resenaId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "La reseña no existe."));
        resenas.guardar(resena.ocultar(administradorId));
        eventos.publicar(new EventoDominio("ResenaOcultada", administradorId, "resena", resenaId, EventoDominio.EXITO, Map.of(), List.of()));
    }

    public record PublicarResenaComando(
            @NotNull @Min(value = 1, message = "La calificación mínima es 1.") @Max(value = 5, message = "La calificación máxima es 5.") Integer calificacion,
            @NotBlank(message = "Escribe tu reseña.") @Size(max = 1000, message = "La reseña admite hasta 1.000 caracteres.") String contenido) {
    }
}
