package com.inversioneslr.marketplace.administracion.infraestructura;

import com.inversioneslr.marketplace.administracion.dominio.Indicadores;
import com.inversioneslr.marketplace.administracion.dominio.RepositorioIndicadores;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lee las tablas de los demás módulos solo para contar: el dashboard no cambia nada. */
@Repository
public class RepositorioIndicadoresSql implements RepositorioIndicadores {

    private static final String EN_EL_PERIODO = "(r.creado_en AT TIME ZONE 'America/Bogota')::date BETWEEN :desde AND :hasta";
    private static final int CIUDADES = 5;

    private final JdbcClient jdbc;

    public RepositorioIndicadoresSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Indicadores calcular(LocalDate desde, LocalDate hasta) {
        long usuarios = jdbc.sql("SELECT count(*) FROM usuario").query(Long.class).single();
        long activas = jdbc.sql("SELECT count(*) FROM publicacion WHERE estado = 'ACTIVA'").query(Long.class).single();

        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (EstadoReserva estado : EstadoReserva.values()) {
            porEstado.put(estado.name(), 0L);
        }
        jdbc.sql("SELECT r.estado, count(*) AS cantidad FROM reserva r WHERE %s GROUP BY r.estado".formatted(EN_EL_PERIODO))
                .param("desde", desde)
                .param("hasta", hasta)
                .query((fila, numero) -> Map.entry(fila.getString("estado"), fila.getLong("cantidad")))
                .list()
                .forEach(cantidad -> porEstado.put(cantidad.getKey(), cantidad.getValue()));

        BigDecimal ingresos = jdbc.sql("""
                SELECT coalesce(sum(r.tarifa_servicio), 0) FROM reserva r
                WHERE r.estado IN ('CONFIRMADA', 'COMPLETADA') AND %s""".formatted(EN_EL_PERIODO))
                .param("desde", desde)
                .param("hasta", hasta)
                .query(BigDecimal.class)
                .single();

        List<String> ciudades = jdbc.sql("""
                SELECT p.ciudad FROM reserva r JOIN publicacion p ON p.id = r.publicacion_id
                WHERE r.estado IN ('CONFIRMADA', 'COMPLETADA') AND p.ciudad IS NOT NULL AND %s
                GROUP BY p.ciudad
                ORDER BY count(*) DESC, p.ciudad
                LIMIT :limite""".formatted(EN_EL_PERIODO))
                .param("desde", desde)
                .param("hasta", hasta)
                .param("limite", CIUDADES)
                .query(String.class)
                .list();

        return new Indicadores(desde, hasta, usuarios, activas, porEstado, ingresos, ciudades);
    }
}
