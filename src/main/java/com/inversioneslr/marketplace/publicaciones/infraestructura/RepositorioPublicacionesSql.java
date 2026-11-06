package com.inversioneslr.marketplace.publicaciones.infraestructura;

import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.TipoAlojamiento;
import com.inversioneslr.marketplace.publicaciones.dominio.EstadoPublicacion;
import com.inversioneslr.marketplace.publicaciones.dominio.PeriodoDisponibilidad;
import com.inversioneslr.marketplace.publicaciones.dominio.PoliticaOfrecida;
import com.inversioneslr.marketplace.publicaciones.dominio.Publicacion;
import com.inversioneslr.marketplace.publicaciones.dominio.RepositorioPublicaciones;
import com.inversioneslr.marketplace.publicaciones.dominio.Servicio;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioPublicacionesSql implements RepositorioPublicaciones {

    private static final String PENDIENTE = "SQL de publicaciones";

    private final JdbcClient jdbc;

    public RepositorioPublicacionesSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Publicacion> buscarPorId(UUID id) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public Optional<DatosPublicacion> buscarDatos(UUID id) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public List<Publicacion> listar(UUID anfitrionId, EstadoPublicacion estado) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public Publicacion guardar(Publicacion publicacion) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public List<PeriodoDisponibilidad> calendario(UUID publicacionId) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public boolean periodoDisponible(UUID publicacionId, LocalDate llegada, LocalDate salida) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public PeriodoDisponibilidad guardarPeriodo(PeriodoDisponibilidad periodo) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public List<TipoAlojamiento> listarTipos() {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public TipoAlojamiento guardarTipo(TipoAlojamiento tipo) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public List<Servicio> listarServicios() {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public List<PoliticaOfrecida> listarPoliticas() {
        throw new UnsupportedOperationException(PENDIENTE);
    }
}
