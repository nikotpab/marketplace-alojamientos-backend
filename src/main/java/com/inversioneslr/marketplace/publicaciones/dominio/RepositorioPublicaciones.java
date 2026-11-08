package com.inversioneslr.marketplace.publicaciones.dominio;

import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.TipoAlojamiento;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioPublicaciones {

    Optional<Publicacion> buscarPorId(UUID id);

    Optional<PublicacionVista> buscarVista(UUID id);

    Optional<DatosPublicacion> buscarDatos(UUID id);

    /** @param anfitrionId null para no filtrar por anfitrión */
    List<Publicacion> listar(UUID anfitrionId, EstadoPublicacion estado);

    Publicacion guardar(Publicacion publicacion);

    /** Periodos definidos por el anfitrión más los ocupados por reservas confirmadas. */
    List<PeriodoDisponibilidad> calendario(UUID publicacionId);

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
