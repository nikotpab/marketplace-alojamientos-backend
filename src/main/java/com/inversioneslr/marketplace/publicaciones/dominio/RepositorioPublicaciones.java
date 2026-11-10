package com.inversioneslr.marketplace.publicaciones.dominio;

import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.TipoAlojamiento;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioPublicaciones {

    Optional<Publicacion> buscarPorId(UUID id);

    Optional<PublicacionVista> buscarVista(UUID id);

    Optional<DatosPublicacion> buscarDatos(UUID id);

    /**
     * De la modificada más recientemente a la más antigua.
     *
     * @param anfitrionId null para no filtrar por anfitrión
     * @param estado      null para no filtrar por estado
     */
    List<PublicacionVista> listarVistas(UUID anfitrionId, EstadoPublicacion estado);

    Publicacion guardar(Publicacion publicacion);

    /** Entre {@code desde} y {@code hasta}, ambos incluidos. */
    Disponibilidad disponibilidad(UUID publicacionId, LocalDate desde, LocalDate hasta);

    /** Si alguna reserva confirmada ocupa una noche de [inicio, fin). */
    boolean hayReservasConfirmadas(UUID publicacionId, LocalDate inicio, LocalDate fin);

    /**
     * Deja disponible [inicio, fin): recorta los bloqueos que se crucen y une el rango con los periodos disponibles
     * que se crucen o lo toquen, para que la estancia quede siempre dentro de un único periodo.
     */
    void liberar(UUID publicacionId, LocalDate inicio, LocalDate fin);

    boolean periodoDisponible(UUID publicacionId, LocalDate llegada, LocalDate salida);

    PeriodoDisponibilidad guardarPeriodo(PeriodoDisponibilidad periodo);

    List<TipoAlojamiento> listarTipos();

    List<TipoConfigurado> listarTiposActivos();

    /** Solo entre los tipos activos. */
    Optional<UUID> buscarTipoPorCodigo(String codigo);

    TipoAlojamiento guardarTipo(TipoAlojamiento tipo);

    List<Servicio> listarServicios();

    List<PoliticaOfrecida> listarPoliticas();
}
