package com.inversioneslr.marketplace.resenas.aplicacion;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicacionId;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.ResenaId;
import com.inversioneslr.marketplace.compartido.ReservaId;
import com.inversioneslr.marketplace.compartido.UsuarioId;
import com.inversioneslr.marketplace.resenas.EventosResenas.ResenaOcultada;
import com.inversioneslr.marketplace.resenas.EventosResenas.ResenaPublicada;
import com.inversioneslr.marketplace.resenas.EventosResenas.ResenaReportada;
import com.inversioneslr.marketplace.resenas.FachadaResenas;
import com.inversioneslr.marketplace.resenas.MotivoReporte;
import com.inversioneslr.marketplace.resenas.dominio.RepositorioResenas;
import com.inversioneslr.marketplace.resenas.dominio.Resena;
import com.inversioneslr.marketplace.resenas.dominio.ResenaGuardada;
import com.inversioneslr.marketplace.resenas.dominio.ResenaReportadaGuardada;
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
     * Solo con una reserva COMPLETADA propia y una reseña por reserva. El contenido se guarda tal cual: el
     * frontend lo muestra con la interpolación de Angular, que escapa el HTML.
     */
    @Transactional
    public ResenaVisible publicar(UUID huespedId, UUID reservaId, PublicarResenaComando comando) {
        DatosReserva reserva = reservas.datosReserva(new ReservaId(reservaId));
        if (!reserva.huespedId().equals(huespedId)) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.ACCESO_DENEGADO, "Esta reserva no es tuya.");
        }
        if (reserva.estado() != EstadoReserva.COMPLETADA) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "Solo puedes reseñar una estadía completada.");
        }
        Resena resena = resenas.guardar(new Resena(UUID.randomUUID(), reservaId, comando.calificacion(), comando.contenido().strip(),
                false, null));
        eventos.publicar(new ResenaPublicada(huespedId, resena.id(), reservaId, reserva.publicacionId(), resena.calificacion()));
        ResenaGuardada guardada = resenas.buscarVisible(resena.id()).orElseThrow();
        UsuarioId autor = new UsuarioId(huespedId);
        return visible(guardada, usuarios.personas(Set.of(autor)).get(autor));
    }

    /** El huésped de cada reserva y su nombre en una consulta a cada fachada, sin leer sus tablas. */
    @Override
    public List<ResenaVisible> listarVisibles(PublicacionId publicacionId) {
        Map<ReservaId, UsuarioId> huespedes = reservas.huespedPorReserva(publicacionId);
        List<ResenaGuardada> guardadas = resenas.listarVisibles(huespedes.keySet().stream().map(ReservaId::valor).toList());
        Map<UsuarioId, Persona> autores = usuarios.personas(guardadas.stream().map(guardada -> huesped(huespedes, guardada)).toList());
        return guardadas.stream()
                .filter(guardada -> autores.containsKey(huesped(huespedes, guardada)))
                .map(guardada -> visible(guardada, autores.get(huesped(huespedes, guardada))))
                .toList();
    }

    private static UsuarioId huesped(Map<ReservaId, UsuarioId> huespedes, ResenaGuardada guardada) {
        return huespedes.get(new ReservaId(guardada.reservaId()));
    }

    private static ResenaVisible visible(ResenaGuardada guardada, Persona huesped) {
        return new ResenaVisible(guardada.id(), autor(huesped), guardada.calificacion(), guardada.contenido(), guardada.creadoEn());
    }

    /**
     * El nombre y la inicial del apellido, para no exponer los datos del huésped. Como el antiguo
     * concat_ws(' ', nombre, left(apellido, 1) || '.'): omite lo que falte.
     */
    private static String autor(Persona huesped) {
        String apellido = huesped.apellido();
        String inicial = null;
        if (apellido != null) {
            int fin = apellido.isEmpty() ? 0 : apellido.offsetByCodePoints(0, 1);
            inicial = apellido.substring(0, fin) + ".";
        }
        return Stream.of(huesped.nombre(), inicial).filter(Objects::nonNull).collect(Collectors.joining(" "));
    }

    /** Queda en la auditoría quién la ocultó. */
    @Override
    @Transactional
    public void ocultar(UsuarioId administradorId, ResenaId resenaId) {
        Resena resena = resenas.buscarPorId(resenaId.valor())
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "La reseña no existe."));
        resenas.guardar(resena.ocultar(administradorId.valor()));
        eventos.publicar(new ResenaOcultada(administradorId.valor(), resenaId.valor()));
    }

    /**
     * Cualquier usuario con sesión reporta una reseña visible, una sola vez y nunca la propia. Queda
     * en la auditoría con el motivo.
     */
    @Transactional
    public void reportar(UUID usuarioId, UUID resenaId, ReportarResenaComando comando) {
        ResenaGuardada resena = resenas.buscarVisible(resenaId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "La reseña no existe."));
        if (reservas.datosReserva(new ReservaId(resena.reservaId())).huespedId().equals(usuarioId)) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "No puedes reportar tu propia reseña.");
        }
        String comentario = comando.comentario() == null || comando.comentario().isBlank() ? null : comando.comentario().strip();
        resenas.reportar(resenaId, usuarioId, comando.motivo().name(), comentario);
        eventos.publicar(new ResenaReportada(usuarioId, resenaId, comando.motivo().name()));
    }

    /** La publicación y el autor de cada una en una consulta a cada fachada, sin leer sus tablas. */
    @Override
    public List<ResenaConReportes> listarReportadas() {
        List<ResenaReportadaGuardada> reportadas = resenas.listarReportadas();
        Map<ReservaId, DatosReserva> reservasDeLasResenas = reservas.datosReservas(
                reportadas.stream().map(resena -> new ReservaId(resena.reservaId())).toList());
        Map<UsuarioId, Persona> autores = usuarios.personas(reservasDeLasResenas.values().stream()
                .map(reserva -> new UsuarioId(reserva.huespedId())).collect(Collectors.toSet()));
        return reportadas.stream()
                .filter(resena -> reservasDeLasResenas.containsKey(new ReservaId(resena.reservaId())))
                .map(resena -> {
                    DatosReserva reserva = reservasDeLasResenas.get(new ReservaId(resena.reservaId()));
                    Persona autor = autores.get(new UsuarioId(reserva.huespedId()));
                    return new ResenaConReportes(resena.id(), reserva.publicacionId(), autor == null ? null : autor(autor),
                            resena.calificacion(), resena.contenido(), resena.creadoEn(), resena.reportes(), resena.motivos(),
                            resena.ultimoReporte());
                })
                .toList();
    }

    public record ReportarResenaComando(
            @NotNull(message = "Elige el motivo del reporte.") MotivoReporte motivo,
            @Size(max = 500, message = "El comentario admite hasta 500 caracteres.") String comentario) {
    }

    public record PublicarResenaComando(
            @NotNull @Min(value = 1, message = "La calificación mínima es 1.") @Max(value = 5, message = "La calificación máxima es 5.") Integer calificacion,
            @NotBlank(message = "Escribe tu reseña.") @Size(max = 1000, message = "La reseña admite hasta 1.000 caracteres.") String contenido) {
    }
}
