package com.inversioneslr.marketplace.publicaciones.infraestructura;

import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.ServicioAdicional;
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
        List<ServicioAdicional> adicionales = jdbc.sql("SELECT id, nombre, costo FROM servicio_adicional WHERE publicacion_id = :id")
                .param("id", id)
                .query((fila, numero) -> new ServicioAdicional(fila.getObject("id", UUID.class), fila.getString("nombre"), fila.getBigDecimal("costo")))
                .list();
        return jdbc.sql("""
                SELECT p.anfitrion_id, p.estado, p.precio_noche, p.capacidad, c.nombre AS politica
                FROM publicacion p
                JOIN politica_cancelacion c ON c.id = p.politica_cancelacion_id
                WHERE p.id = :id""")
                .param("id", id)
                .query((fila, numero) -> new DatosPublicacion(
                        id,
                        fila.getObject("anfitrion_id", UUID.class),
                        EstadoPublicacion.ACTIVA.name().equals(fila.getString("estado")),
                        fila.getBigDecimal("precio_noche"),
                        fila.getInt("capacidad"),
                        fila.getString("politica"),
                        adicionales))
                .optional();
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
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM periodo_disponibilidad
                               WHERE publicacion_id = :publicacion AND tipo = 'DISPONIBLE'
                                 AND fecha_inicio <= :llegada AND fecha_fin >= :salida)
                   AND NOT EXISTS (SELECT 1 FROM periodo_disponibilidad
                                   WHERE publicacion_id = :publicacion AND tipo = 'BLOQUEADO'
                                     AND daterange(fecha_inicio, fecha_fin, '[)') && daterange(CAST(:llegada AS date), CAST(:salida AS date), '[)'))
                   AND NOT EXISTS (SELECT 1 FROM reserva
                                   WHERE publicacion_id = :publicacion AND estado = 'CONFIRMADA'
                                     AND daterange(fecha_llegada, fecha_salida, '[)') && daterange(CAST(:llegada AS date), CAST(:salida AS date), '[)'))""")
                .param("publicacion", publicacionId)
                .param("llegada", llegada)
                .param("salida", salida)
                .query(Boolean.class)
                .single();
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
