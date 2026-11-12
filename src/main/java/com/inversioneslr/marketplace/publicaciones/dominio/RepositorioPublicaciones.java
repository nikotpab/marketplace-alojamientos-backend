package com.inversioneslr.marketplace.publicaciones.dominio;

import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.TipoAlojamiento;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioPublicaciones {

    Optional<Publicacion> buscarPorId(UUID id);

    Optional<PublicacionConAnfitrion> buscarVista(UUID id);

    Optional<DatosPublicacion> buscarDatos(UUID id);

    /**
     * De la modificada más recientemente a la más antigua.
     *
     * @param anfitrionId null para no filtrar por anfitrión
     * @param estado      null para no filtrar por estado
     */
    List<PublicacionConAnfitrion> listarVistas(UUID anfitrionId, EstadoPublicacion estado);

    Publicacion guardar(Publicacion publicacion);

    /**
     * Días entre {@code desde} y {@code hasta}, ambos incluidos, sin periodo disponible o bloqueados por el anfitrión.
     * Las reservas no se leen aquí: las da {@link com.inversioneslr.marketplace.publicaciones.OcupacionReservas}.
     */
    List<LocalDate> diasNoDisponibles(UUID publicacionId, LocalDate desde, LocalDate hasta);

    /**
     * Deja disponible [inicio, fin): recorta los bloqueos que se crucen y une el rango con los periodos disponibles
     * que se crucen o lo toquen, para que la estancia quede siempre dentro de un único periodo.
     */
    void liberar(UUID publicacionId, LocalDate inicio, LocalDate fin);

    /** Según los periodos propios: [llegada, salida) cabe en un periodo disponible y no toca un bloqueo. */
    boolean periodoDisponible(UUID publicacionId, LocalDate llegada, LocalDate salida);

    /** Título de cada publicación; las que no existen no aparecen. */
    Map<UUID, String> titulos(Collection<UUID> ids);

    List<UUID> idsDelAnfitrion(UUID anfitrionId);

    PeriodoDisponibilidad guardarPeriodo(PeriodoDisponibilidad periodo);

    List<TipoAlojamiento> listarTipos();

    List<TipoConfigurado> listarTiposActivos();

    /** Solo entre los tipos activos. */
    Optional<UUID> buscarTipoPorCodigo(String codigo);

    TipoAlojamiento guardarTipo(TipoAlojamiento tipo);

    List<Servicio> listarServicios();

    List<PoliticaOfrecida> listarPoliticas();
}
