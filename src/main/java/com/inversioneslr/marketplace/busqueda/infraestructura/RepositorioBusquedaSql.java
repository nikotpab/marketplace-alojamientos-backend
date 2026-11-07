package com.inversioneslr.marketplace.busqueda.infraestructura;

import com.inversioneslr.marketplace.busqueda.dominio.AlojamientoDetalle;
import com.inversioneslr.marketplace.busqueda.dominio.AlojamientoResumen;
import com.inversioneslr.marketplace.busqueda.dominio.FiltrosBusqueda;
import com.inversioneslr.marketplace.busqueda.dominio.PaginaAlojamientos;
import com.inversioneslr.marketplace.busqueda.dominio.RepositorioBusqueda;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * ADR-02: una sola consulta con los filtros, la disponibilidad y la paginación, con tiempo máximo.
 * La caché, si la prueba de carga la exige, sería un Decorator de este repositorio.
 */
@Repository
public class RepositorioBusquedaSql implements RepositorioBusqueda {

    private static final int TIEMPO_MAX_CONSULTA_SEGUNDOS = 2;

    /** Calificación promedio de las reseñas visibles; la usan la búsqueda y el detalle. */
    static final String CALIFICACION = """
            (SELECT round(avg(r.calificacion), 1) FROM resena r JOIN reserva rv ON rv.id = r.reserva_id
             WHERE rv.publicacion_id = p.id AND NOT r.oculta)""";

    static final String SERVICIOS = """
            ARRAY(SELECT s.nombre FROM publicacion_servicio ps JOIN servicio s ON s.id = ps.servicio_id
                  WHERE ps.publicacion_id = p.id ORDER BY s.nombre)""";

    private final JdbcClient jdbc;

    public RepositorioBusquedaSql(DataSource baseDeDatos) {
        JdbcTemplate plantilla = new JdbcTemplate(baseDeDatos);
        plantilla.setQueryTimeout(TIEMPO_MAX_CONSULTA_SEGUNDOS);
        this.jdbc = JdbcClient.create(plantilla);
    }

    @Override
    public PaginaAlojamientos buscar(FiltrosBusqueda filtros, int pagina, int tamanoPagina) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.titulo, p.ciudad, t.codigo AS tipo, p.capacidad, p.precio_noche,
                       %s AS calificacion,
                       (SELECT i.ruta FROM imagen_publicacion i WHERE i.publicacion_id = p.id ORDER BY i.orden LIMIT 1) AS imagen,
                       %s AS servicios,
                       count(*) OVER () AS total
                FROM publicacion p
                JOIN tipo_alojamiento t ON t.id = p.tipo_alojamiento_id
                WHERE p.estado = 'ACTIVA'""".formatted(CALIFICACION, SERVICIOS));
        Map<String, Object> parametros = new HashMap<>();
        if (filtros.ciudad() != null && !filtros.ciudad().isBlank()) {
            filtrar(sql, parametros, "unaccent(lower(p.ciudad)) = unaccent(lower(:ciudad))", "ciudad", filtros.ciudad().trim());
        }
        filtrar(sql, parametros, "p.capacidad >= :huespedes", "huespedes", filtros.huespedes());
        filtrar(sql, parametros, "t.codigo = :tipo", "tipo", vacioComoNulo(filtros.tipo()));
        filtrar(sql, parametros, "p.precio_noche >= :precioMin", "precioMin", filtros.precioMin());
        filtrar(sql, parametros, "p.precio_noche <= :precioMax", "precioMax", filtros.precioMax());
        filtrarServicios(sql, parametros, filtros.servicios());
        filtrarDisponibilidad(sql, parametros, filtros);
        sql.append(" ORDER BY calificacion DESC NULLS LAST, p.precio_noche, p.id LIMIT :tamano OFFSET :desde");
        parametros.put("tamano", tamanoPagina);
        parametros.put("desde", pagina * tamanoPagina);

        long[] total = {0};
        List<AlojamientoResumen> contenido = jdbc.sql(sql.toString()).params(parametros).query((fila, numero) -> {
            total[0] = fila.getLong("total");
            return aResumen(fila);
        }).list();
        int totalPaginas = (int) ((total[0] + tamanoPagina - 1) / tamanoPagina);
        return new PaginaAlojamientos(contenido, total[0], pagina, totalPaginas);
    }

    @Override
    public Optional<AlojamientoDetalle> buscarDetalle(UUID publicacionId) {
        throw new UnsupportedOperationException("SQL de búsqueda");
    }

    /** Debe tener todos los servicios pedidos. */
    private static void filtrarServicios(StringBuilder sql, Map<String, Object> parametros, List<String> servicios) {
        if (servicios == null || servicios.isEmpty()) {
            return;
        }
        List<String> distintos = servicios.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        sql.append(' ').append("""
                 AND (SELECT count(*) FROM publicacion_servicio ps JOIN servicio s ON s.id = ps.servicio_id
                      WHERE ps.publicacion_id = p.id AND s.nombre IN (:servicios)) = :cantidadServicios""");
        parametros.put("servicios", distintos);
        parametros.put("cantidadServicios", distintos.size());
    }

    /** Las mismas reglas que verifica la reserva: dentro de un periodo disponible, sin bloqueos ni reservas cruzadas. */
    private static void filtrarDisponibilidad(StringBuilder sql, Map<String, Object> parametros, FiltrosBusqueda filtros) {
        if (filtros.llegada() == null) {
            return;
        }
        sql.append(' ').append("""
                 AND EXISTS (SELECT 1 FROM periodo_disponibilidad d
                             WHERE d.publicacion_id = p.id AND d.tipo = 'DISPONIBLE'
                               AND d.fecha_inicio <= CAST(:llegada AS date) AND d.fecha_fin >= CAST(:salida AS date))
                 AND NOT EXISTS (SELECT 1 FROM periodo_disponibilidad d
                                 WHERE d.publicacion_id = p.id AND d.tipo = 'BLOQUEADO'
                                   AND daterange(d.fecha_inicio, d.fecha_fin, '[)') && daterange(CAST(:llegada AS date), CAST(:salida AS date), '[)'))
                 AND NOT EXISTS (SELECT 1 FROM reserva r
                                 WHERE r.publicacion_id = p.id AND r.estado = 'CONFIRMADA'
                                   AND daterange(r.fecha_llegada, r.fecha_salida, '[)') && daterange(CAST(:llegada AS date), CAST(:salida AS date), '[)'))""");
        parametros.put("llegada", filtros.llegada());
        parametros.put("salida", filtros.salida());
    }

    private static void filtrar(StringBuilder sql, Map<String, Object> parametros, String condicion, String nombre, Object valor) {
        if (valor != null) {
            sql.append(" AND ").append(condicion);
            parametros.put(nombre, valor);
        }
    }

    private static String vacioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    private static AlojamientoResumen aResumen(ResultSet fila) throws SQLException {
        return new AlojamientoResumen(
                fila.getObject("id", UUID.class),
                fila.getString("titulo"),
                fila.getString("ciudad"),
                fila.getString("tipo"),
                fila.getInt("capacidad"),
                fila.getBigDecimal("precio_noche"),
                fila.getBigDecimal("calificacion"),
                fila.getString("imagen"),
                textos(fila.getArray("servicios")));
    }

    static List<String> textos(Array arreglo) throws SQLException {
        return arreglo == null ? List.of() : List.of((String[]) arreglo.getArray());
    }
}
