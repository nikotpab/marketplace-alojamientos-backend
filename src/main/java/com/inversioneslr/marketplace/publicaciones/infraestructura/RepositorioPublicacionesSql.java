package com.inversioneslr.marketplace.publicaciones.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.ServicioAdicional;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.TipoAlojamiento;
import com.inversioneslr.marketplace.publicaciones.dominio.EstadoPublicacion;
import com.inversioneslr.marketplace.publicaciones.dominio.PeriodoDisponibilidad;
import com.inversioneslr.marketplace.publicaciones.dominio.PoliticaOfrecida;
import com.inversioneslr.marketplace.publicaciones.dominio.Publicacion;
import com.inversioneslr.marketplace.publicaciones.dominio.RepositorioPublicaciones;
import com.inversioneslr.marketplace.publicaciones.dominio.Servicio;
import com.inversioneslr.marketplace.publicaciones.dominio.TipoConfigurado;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
        List<UUID> servicios = jdbc.sql("SELECT servicio_id FROM publicacion_servicio WHERE publicacion_id = :id")
                .param("id", id).query(UUID.class).list();
        List<String> reglas = jdbc.sql("SELECT descripcion FROM regla_alojamiento WHERE publicacion_id = :id ORDER BY creado_en, descripcion")
                .param("id", id).query(String.class).list();
        List<String> imagenes = jdbc.sql("SELECT ruta FROM imagen_publicacion WHERE publicacion_id = :id ORDER BY orden")
                .param("id", id).query(String.class).list();
        return jdbc.sql("SELECT * FROM publicacion WHERE id = :id")
                .param("id", id)
                .query((fila, numero) -> new Publicacion(
                        id,
                        fila.getObject("anfitrion_id", UUID.class),
                        fila.getObject("tipo_alojamiento_id", UUID.class),
                        fila.getObject("politica_cancelacion_id", UUID.class),
                        fila.getString("titulo"),
                        fila.getString("descripcion"),
                        fila.getString("ciudad"),
                        fila.getString("ubicacion_textual"),
                        fila.getObject("capacidad", Integer.class),
                        fila.getBigDecimal("precio_noche"),
                        servicios,
                        reglas,
                        imagenes,
                        EstadoPublicacion.valueOf(fila.getString("estado")),
                        fila.getString("motivo_estado")))
                .optional();
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
        try {
            jdbc.sql("""
                    INSERT INTO publicacion (id, anfitrion_id, tipo_alojamiento_id, politica_cancelacion_id, titulo, descripcion,
                                             ciudad, ubicacion_textual, capacidad, precio_noche, estado, motivo_estado)
                    VALUES (:id, :anfitrion, :tipo, :politica, :titulo, :descripcion,
                            :ciudad, :ubicacion, :capacidad, :precio, :estado, :motivo)
                    ON CONFLICT (id) DO UPDATE SET
                        tipo_alojamiento_id = EXCLUDED.tipo_alojamiento_id,
                        politica_cancelacion_id = EXCLUDED.politica_cancelacion_id,
                        titulo = EXCLUDED.titulo,
                        descripcion = EXCLUDED.descripcion,
                        ciudad = EXCLUDED.ciudad,
                        ubicacion_textual = EXCLUDED.ubicacion_textual,
                        capacidad = EXCLUDED.capacidad,
                        precio_noche = EXCLUDED.precio_noche,
                        estado = EXCLUDED.estado,
                        motivo_estado = EXCLUDED.motivo_estado""")
                    .param("id", publicacion.id())
                    .param("anfitrion", publicacion.anfitrionId())
                    .param("tipo", publicacion.tipoAlojamientoId())
                    .param("politica", publicacion.politicaCancelacionId())
                    .param("titulo", publicacion.titulo())
                    .param("descripcion", publicacion.descripcion())
                    .param("ciudad", publicacion.ciudad())
                    .param("ubicacion", publicacion.ubicacionTextual())
                    .param("capacidad", publicacion.capacidad())
                    .param("precio", publicacion.precioNoche())
                    .param("estado", publicacion.estado().name())
                    .param("motivo", publicacion.motivoEstado())
                    .update();
            reemplazarDetalles(publicacion);
        } catch (DataIntegrityViolationException e) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS,
                    "El tipo de alojamiento, la política o algún servicio no existen. Elige una opción del catálogo.");
        }
        return publicacion;
    }

    private void reemplazarDetalles(Publicacion publicacion) {
        UUID id = publicacion.id();
        jdbc.sql("DELETE FROM publicacion_servicio WHERE publicacion_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM regla_alojamiento WHERE publicacion_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM imagen_publicacion WHERE publicacion_id = :id").param("id", id).update();
        publicacion.servicios().forEach(servicio ->
                jdbc.sql("INSERT INTO publicacion_servicio (publicacion_id, servicio_id) VALUES (:id, :servicio)")
                        .param("id", id).param("servicio", servicio).update());
        publicacion.reglas().forEach(regla ->
                jdbc.sql("INSERT INTO regla_alojamiento (publicacion_id, descripcion) VALUES (:id, :regla)")
                        .param("id", id).param("regla", regla).update());
        for (int orden = 0; orden < publicacion.imagenes().size(); orden++) {
            jdbc.sql("INSERT INTO imagen_publicacion (publicacion_id, ruta, orden) VALUES (:id, :ruta, :orden)")
                    .param("id", id).param("ruta", publicacion.imagenes().get(orden)).param("orden", orden).update();
        }
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
    public List<TipoConfigurado> listarTiposActivos() {
        return jdbc.sql("SELECT codigo, nombre FROM tipo_alojamiento WHERE activo ORDER BY nombre")
                .query((fila, numero) -> new TipoConfigurado(fila.getString("codigo"), fila.getString("nombre")))
                .list();
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
