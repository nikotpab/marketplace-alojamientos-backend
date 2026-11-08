package com.inversioneslr.marketplace.publicaciones.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.ServicioAdicional;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.TipoAlojamiento;
import com.inversioneslr.marketplace.publicaciones.dominio.Disponibilidad;
import com.inversioneslr.marketplace.publicaciones.dominio.EstadoPublicacion;
import com.inversioneslr.marketplace.publicaciones.dominio.PeriodoDisponibilidad;
import com.inversioneslr.marketplace.publicaciones.dominio.PoliticaOfrecida;
import com.inversioneslr.marketplace.publicaciones.dominio.Publicacion;
import com.inversioneslr.marketplace.publicaciones.dominio.PublicacionVista;
import com.inversioneslr.marketplace.publicaciones.dominio.RepositorioPublicaciones;
import com.inversioneslr.marketplace.publicaciones.dominio.Servicio;
import com.inversioneslr.marketplace.publicaciones.dominio.TipoConfigurado;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioPublicacionesSql implements RepositorioPublicaciones {

    private static final String PENDIENTE = "SQL de publicaciones";

    /** Consulta base de la vista para el frontend; se completa con el filtro y el orden. */
    private static final String VISTA = """
            SELECT p.id, p.estado, p.actualizado_en, p.motivo_estado, concat_ws(' ', u.nombre, u.apellido) AS anfitrion,
                   p.titulo, p.descripcion, p.ubicacion_textual, t.codigo AS tipo, p.capacidad, p.precio_noche,
                   ARRAY(SELECT s.nombre FROM publicacion_servicio ps JOIN servicio s ON s.id = ps.servicio_id
                         WHERE ps.publicacion_id = p.id ORDER BY s.nombre) AS servicios,
                   ARRAY(SELECT r.descripcion FROM regla_alojamiento r WHERE r.publicacion_id = p.id ORDER BY r.orden, r.creado_en) AS reglas,
                   ARRAY(SELECT i.ruta FROM imagen_publicacion i WHERE i.publicacion_id = p.id ORDER BY i.orden) AS imagenes,
                   c.nombre AS politica
            FROM publicacion p
            JOIN usuario u ON u.id = p.anfitrion_id
            LEFT JOIN tipo_alojamiento t ON t.id = p.tipo_alojamiento_id
            LEFT JOIN politica_cancelacion c ON c.id = p.politica_cancelacion_id
            """;

    private final JdbcClient jdbc;

    public RepositorioPublicacionesSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Publicacion> buscarPorId(UUID id) {
        List<UUID> servicios = jdbc.sql("SELECT servicio_id FROM publicacion_servicio WHERE publicacion_id = :id")
                .param("id", id).query(UUID.class).list();
        List<String> reglas = jdbc.sql("SELECT descripcion FROM regla_alojamiento WHERE publicacion_id = :id ORDER BY orden, creado_en")
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
    public Optional<PublicacionVista> buscarVista(UUID id) {
        return jdbc.sql(VISTA + "WHERE p.id = :id")
                .param("id", id)
                .query(RepositorioPublicacionesSql::aVista)
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
                LEFT JOIN politica_cancelacion c ON c.id = p.politica_cancelacion_id
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
    public List<PublicacionVista> listarVistas(UUID anfitrionId, EstadoPublicacion estado) {
        return jdbc.sql(VISTA + """
                WHERE (CAST(:anfitrion AS uuid) IS NULL OR p.anfitrion_id = :anfitrion)
                  AND (CAST(:estado AS text) IS NULL OR p.estado = :estado)
                ORDER BY p.actualizado_en DESC, p.id""")
                .param("anfitrion", anfitrionId)
                .param("estado", estado == null ? null : estado.name())
                .query(RepositorioPublicacionesSql::aVista)
                .list();
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
                        motivo_estado = EXCLUDED.motivo_estado,
                        actualizado_en = now()""")
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
        for (int orden = 0; orden < publicacion.reglas().size(); orden++) {
            jdbc.sql("INSERT INTO regla_alojamiento (publicacion_id, descripcion, orden) VALUES (:id, :regla, :orden)")
                    .param("id", id).param("regla", publicacion.reglas().get(orden)).param("orden", orden).update();
        }
        for (int orden = 0; orden < publicacion.imagenes().size(); orden++) {
            jdbc.sql("INSERT INTO imagen_publicacion (publicacion_id, ruta, orden) VALUES (:id, :ruta, :orden)")
                    .param("id", id).param("ruta", publicacion.imagenes().get(orden)).param("orden", orden).update();
        }
    }

    @Override
    public Disponibilidad disponibilidad(UUID publicacionId, LocalDate desde, LocalDate hasta) {
        List<LocalDate> bloqueados = new ArrayList<>();
        List<LocalDate> reservados = new ArrayList<>();
        jdbc.sql("""
                SELECT dia::date AS dia,
                       EXISTS (SELECT 1 FROM reserva r WHERE r.publicacion_id = :id AND r.estado = 'CONFIRMADA'
                                 AND dia >= r.fecha_llegada AND dia < r.fecha_salida) AS reservado
                FROM generate_series(CAST(:desde AS date), CAST(:hasta AS date), interval '1 day') AS dia
                WHERE NOT EXISTS (SELECT 1 FROM periodo_disponibilidad d WHERE d.publicacion_id = :id AND d.tipo = 'DISPONIBLE'
                                    AND dia >= d.fecha_inicio AND dia < d.fecha_fin)
                   OR EXISTS (SELECT 1 FROM periodo_disponibilidad d WHERE d.publicacion_id = :id AND d.tipo = 'BLOQUEADO'
                                AND dia >= d.fecha_inicio AND dia < d.fecha_fin)
                   OR EXISTS (SELECT 1 FROM reserva r WHERE r.publicacion_id = :id AND r.estado = 'CONFIRMADA'
                                AND dia >= r.fecha_llegada AND dia < r.fecha_salida)
                ORDER BY dia""")
                .param("id", publicacionId)
                .param("desde", desde)
                .param("hasta", hasta)
                .query(fila -> {
                    (fila.getBoolean("reservado") ? reservados : bloqueados).add(fila.getObject("dia", LocalDate.class));
                });
        return new Disponibilidad(bloqueados, reservados);
    }

    @Override
    public boolean hayReservasConfirmadas(UUID publicacionId, LocalDate inicio, LocalDate fin) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM reserva WHERE publicacion_id = :id AND estado = 'CONFIRMADA'
                                 AND daterange(fecha_llegada, fecha_salida, '[)') && daterange(CAST(:inicio AS date), CAST(:fin AS date), '[)'))""")
                .param("id", publicacionId)
                .param("inicio", inicio)
                .param("fin", fin)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void liberar(UUID publicacionId, LocalDate inicio, LocalDate fin) {
        // Conserva las partes de cada bloqueo que quedan antes y después del rango, y borra el resto.
        jdbc.sql("""
                INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo)
                SELECT publicacion_id, fecha_inicio, :inicio, 'BLOQUEADO' FROM periodo_disponibilidad
                WHERE publicacion_id = :id AND tipo = 'BLOQUEADO' AND fecha_inicio < :inicio AND fecha_fin > :inicio
                UNION ALL
                SELECT publicacion_id, :fin, fecha_fin, 'BLOQUEADO' FROM periodo_disponibilidad
                WHERE publicacion_id = :id AND tipo = 'BLOQUEADO' AND fecha_inicio < :fin AND fecha_fin > :fin""")
                .param("id", publicacionId).param("inicio", inicio).param("fin", fin).update();
        jdbc.sql("""
                DELETE FROM periodo_disponibilidad
                WHERE publicacion_id = :id AND tipo = 'BLOQUEADO' AND fecha_inicio < :fin AND fecha_fin > :inicio""")
                .param("id", publicacionId).param("inicio", inicio).param("fin", fin).update();
        jdbc.sql("""
                WITH unidos AS (
                    DELETE FROM periodo_disponibilidad
                    WHERE publicacion_id = :id AND tipo = 'DISPONIBLE' AND fecha_inicio <= :fin AND fecha_fin >= :inicio
                    RETURNING fecha_inicio, fecha_fin)
                INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo)
                SELECT :id, LEAST(CAST(:inicio AS date), min(fecha_inicio)), GREATEST(CAST(:fin AS date), max(fecha_fin)), 'DISPONIBLE'
                FROM unidos""")
                .param("id", publicacionId).param("inicio", inicio).param("fin", fin).update();
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
        jdbc.sql("INSERT INTO periodo_disponibilidad (id, publicacion_id, fecha_inicio, fecha_fin, tipo) VALUES (:id, :publicacion, :inicio, :fin, :tipo)")
                .param("id", periodo.id())
                .param("publicacion", periodo.publicacionId())
                .param("inicio", periodo.fechaInicio())
                .param("fin", periodo.fechaFin())
                .param("tipo", periodo.tipo().name())
                .update();
        return periodo;
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
    public Optional<UUID> buscarTipoPorCodigo(String codigo) {
        return jdbc.sql("SELECT id FROM tipo_alojamiento WHERE codigo = :codigo AND activo")
                .param("codigo", codigo)
                .query(UUID.class)
                .optional();
    }

    @Override
    public TipoAlojamiento guardarTipo(TipoAlojamiento tipo) {
        throw new UnsupportedOperationException(PENDIENTE);
    }

    @Override
    public List<Servicio> listarServicios() {
        return jdbc.sql("SELECT id, nombre FROM servicio ORDER BY nombre")
                .query((fila, numero) -> new Servicio(fila.getObject("id", UUID.class), fila.getString("nombre")))
                .list();
    }

    @Override
    public List<PoliticaOfrecida> listarPoliticas() {
        return jdbc.sql("SELECT id, nombre FROM politica_cancelacion ORDER BY nombre")
                .query((fila, numero) -> new PoliticaOfrecida(fila.getObject("id", UUID.class), fila.getString("nombre")))
                .list();
    }

    private static PublicacionVista aVista(ResultSet fila, int numero) throws SQLException {
        EstadoPublicacion estado = EstadoPublicacion.valueOf(fila.getString("estado"));
        return new PublicacionVista(
                fila.getObject("id", UUID.class),
                estado,
                fila.getTimestamp("actualizado_en").toInstant(),
                estado == EstadoPublicacion.BORRADOR ? fila.getString("motivo_estado") : null,
                fila.getString("anfitrion"),
                fila.getString("titulo"),
                fila.getString("descripcion"),
                fila.getString("ubicacion_textual"),
                fila.getString("tipo"),
                fila.getObject("capacidad", Integer.class),
                fila.getBigDecimal("precio_noche"),
                textos(fila.getArray("servicios")),
                textos(fila.getArray("reglas")),
                textos(fila.getArray("imagenes")),
                fila.getString("politica"));
    }

    private static List<String> textos(Array arreglo) throws SQLException {
        return arreglo == null ? List.of() : List.of((String[]) arreglo.getArray());
    }
}
