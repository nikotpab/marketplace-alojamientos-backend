package com.inversioneslr.marketplace.busqueda.infraestructura;

import com.inversioneslr.marketplace.busqueda.dominio.AlojamientoDetalle;
import com.inversioneslr.marketplace.busqueda.dominio.AlojamientoResumen;
import com.inversioneslr.marketplace.busqueda.dominio.FiltrosBusqueda;
import com.inversioneslr.marketplace.busqueda.dominio.RepositorioBusqueda;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** ADR-02: una sola consulta indexada con los 6 filtros y tiempo máximo. La caché, si hace falta, sería un Decorator. */
@Repository
public class RepositorioBusquedaSql implements RepositorioBusqueda {

    private static final int TIEMPO_MAX_CONSULTA_SEGUNDOS = 2;

    private final JdbcClient jdbc;

    public RepositorioBusquedaSql(DataSource baseDeDatos) {
        JdbcTemplate plantilla = new JdbcTemplate(baseDeDatos);
        plantilla.setQueryTimeout(TIEMPO_MAX_CONSULTA_SEGUNDOS);
        this.jdbc = JdbcClient.create(plantilla);
    }

    @Override
    public List<AlojamientoResumen> buscar(FiltrosBusqueda filtros, int pagina, int tamanoPagina) {
        throw new UnsupportedOperationException("SQL de búsqueda");
    }

    @Override
    public Optional<AlojamientoDetalle> buscarDetalle(UUID publicacionId) {
        throw new UnsupportedOperationException("SQL de búsqueda");
    }
}
