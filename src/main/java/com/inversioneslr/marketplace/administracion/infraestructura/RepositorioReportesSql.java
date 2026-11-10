package com.inversioneslr.marketplace.administracion.infraestructura;

import com.inversioneslr.marketplace.administracion.dominio.FiltrosReporte;
import com.inversioneslr.marketplace.administracion.dominio.Reporte;
import com.inversioneslr.marketplace.administracion.dominio.RepositorioReportes;
import com.inversioneslr.marketplace.administracion.dominio.TipoReporte;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Como el dashboard, solo lee las tablas de los demás módulos. Los nombres de las columnas son los encabezados. */
@Repository
public class RepositorioReportesSql implements RepositorioReportes {

    private static final int MAXIMO_FILAS = 10_000;

    /** %s es la columna de fecha que filtra el periodo. */
    private static final String PERIODO = """
            (CAST(:desde AS date) IS NULL OR (%1$s AT TIME ZONE 'America/Bogota')::date >= :desde)
            AND (CAST(:hasta AS date) IS NULL OR (%1$s AT TIME ZONE 'America/Bogota')::date <= :hasta)""";
    private static final String CIUDAD = "(CAST(:ciudad AS text) IS NULL OR lower(unaccent(p.ciudad)) = lower(unaccent(:ciudad)))";
    private static final String FECHA = "to_char(%s AT TIME ZONE 'America/Bogota', 'YYYY-MM-DD')";

    private final JdbcClient jdbc;

    public RepositorioReportesSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Reporte consultar(TipoReporte tipo, FiltrosReporte filtros) {
        return switch (tipo) {
            case USUARIOS -> consultar("Usuarios", """
                    SELECT u.nombre AS "Nombre", u.apellido AS "Apellido", u.correo AS "Correo", u.rol AS "Rol",
                           CASE WHEN u.restringido THEN 'Restringida' WHEN u.cuenta_activa THEN 'Activa' ELSE 'Desactivada' END AS "Cuenta",
                           %s AS "Registro"
                    FROM usuario u
                    WHERE %s
                    ORDER BY u.creado_en DESC
                    LIMIT :maximo""".formatted(FECHA.formatted("u.creado_en"), PERIODO.formatted("u.creado_en")), filtros);
            case PUBLICACIONES -> consultar("Publicaciones", """
                    SELECT p.titulo AS "Título", p.ciudad AS "Ciudad", t.nombre AS "Tipo", p.estado AS "Estado",
                           p.precio_noche AS "Precio por noche", concat_ws(' ', u.nombre, u.apellido) AS "Anfitrión", %s AS "Creación"
                    FROM publicacion p
                    JOIN tipo_alojamiento t ON t.id = p.tipo_alojamiento_id
                    JOIN usuario u ON u.id = p.anfitrion_id
                    WHERE %s AND %s
                    ORDER BY p.creado_en DESC
                    LIMIT :maximo""".formatted(FECHA.formatted("p.creado_en"), PERIODO.formatted("p.creado_en"), CIUDAD), filtros);
            case RESERVAS -> consultar("Reservas", """
                    SELECT r.codigo AS "Código", p.titulo AS "Alojamiento", p.ciudad AS "Ciudad", r.fecha_llegada AS "Llegada",
                           r.fecha_salida AS "Salida", r.estado AS "Estado", r.total AS "Total", %s AS "Reservada el"
                    FROM reserva r
                    JOIN publicacion p ON p.id = r.publicacion_id
                    WHERE %s AND %s
                    ORDER BY r.creado_en DESC
                    LIMIT :maximo""".formatted(FECHA.formatted("r.creado_en"), PERIODO.formatted("r.creado_en"), CIUDAD), filtros);
            case INGRESOS_DEMANDA -> consultar("Ingresos y demanda por ciudad", """
                    SELECT p.ciudad AS "Ciudad", count(*) AS "Reservas", sum(r.tarifa_servicio) AS "Ingresos estimados",
                           sum(r.total) AS "Total reservado"
                    FROM reserva r
                    JOIN publicacion p ON p.id = r.publicacion_id
                    WHERE r.estado IN ('CONFIRMADA', 'COMPLETADA') AND %s AND %s
                    GROUP BY p.ciudad
                    ORDER BY count(*) DESC, p.ciudad
                    LIMIT :maximo""".formatted(PERIODO.formatted("r.creado_en"), CIUDAD), filtros);
        };
    }

    /** El SQL solo se arma con las constantes de esta clase; los filtros van como parámetros. */
    private Reporte consultar(String titulo, String sql, FiltrosReporte filtros) {
        return jdbc.sql(sql)
                .param("desde", filtros.desde())
                .param("hasta", filtros.hasta())
                .param("ciudad", filtros.ciudad())
                .param("maximo", MAXIMO_FILAS)
                .query(resultado -> {
                    ResultSetMetaData metadatos = resultado.getMetaData();
                    List<String> columnas = new ArrayList<>();
                    for (int i = 1; i <= metadatos.getColumnCount(); i++) {
                        columnas.add(metadatos.getColumnLabel(i));
                    }
                    List<List<String>> filas = new ArrayList<>();
                    while (resultado.next()) {
                        List<String> fila = new ArrayList<>();
                        for (int i = 1; i <= columnas.size(); i++) {
                            fila.add(Objects.toString(resultado.getString(i), ""));
                        }
                        filas.add(fila);
                    }
                    return new Reporte(titulo, columnas, filas);
                });
    }
}
