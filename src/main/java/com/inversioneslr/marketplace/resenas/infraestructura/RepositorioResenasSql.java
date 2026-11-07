package com.inversioneslr.marketplace.resenas.infraestructura;

import com.inversioneslr.marketplace.resenas.api.FachadaResenas.ResenaVisible;
import com.inversioneslr.marketplace.resenas.dominio.RepositorioResenas;
import com.inversioneslr.marketplace.resenas.dominio.Resena;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioResenasSql implements RepositorioResenas {

    private final JdbcClient jdbc;

    public RepositorioResenasSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Resena guardar(Resena resena) {
        throw new UnsupportedOperationException("SQL de resena");
    }

    @Override
    public Optional<Resena> buscarPorId(UUID id) {
        throw new UnsupportedOperationException("SQL de resena");
    }

    @Override
    public List<ResenaVisible> listarVisibles(UUID publicacionId) {
        return jdbc.sql("""
                SELECT r.id, concat_ws(' ', u.nombre, left(u.apellido, 1) || '.') AS autor, r.calificacion, r.contenido, r.creado_en
                FROM resena r
                JOIN reserva rv ON rv.id = r.reserva_id
                JOIN usuario u ON u.id = rv.huesped_id
                WHERE rv.publicacion_id = :publicacion AND NOT r.oculta
                ORDER BY r.creado_en DESC""")
                .param("publicacion", publicacionId)
                .query((fila, numero) -> new ResenaVisible(
                        fila.getObject("id", UUID.class),
                        fila.getString("autor"),
                        fila.getInt("calificacion"),
                        fila.getString("contenido"),
                        fila.getTimestamp("creado_en").toInstant()))
                .list();
    }
}
