package com.inversioneslr.marketplace.resenas.infraestructura;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.resenas.FachadaResenas.ResenaVisible;
import com.inversioneslr.marketplace.resenas.dominio.RepositorioResenas;
import com.inversioneslr.marketplace.resenas.dominio.Resena;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioResenasSql implements RepositorioResenas {

    /** El autor es el nombre y la inicial del apellido del huésped, para no exponer sus datos. */
    private static final String VISIBLES = """
            SELECT r.id, concat_ws(' ', u.nombre, left(u.apellido, 1) || '.') AS autor, r.calificacion, r.contenido, r.creado_en
            FROM resena r
            JOIN reserva rv ON rv.id = r.reserva_id
            JOIN usuario u ON u.id = rv.huesped_id
            WHERE NOT r.oculta""";
    private static final String VISIBLE_POR_ID = VISIBLES + " AND r.id = :id";
    private static final String VISIBLES_DE_PUBLICACION = VISIBLES + " AND rv.publicacion_id = :publicacion ORDER BY r.creado_en DESC";

    private final JdbcClient jdbc;

    public RepositorioResenasSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserta la reseña o, si ya existe, guarda si está oculta. Una reserva admite una sola reseña. */
    @Override
    public Resena guardar(Resena resena) {
        try {
            jdbc.sql("""
                    INSERT INTO resena (id, reserva_id, calificacion, contenido, oculta, ocultada_por)
                    VALUES (:id, :reserva, :calificacion, :contenido, :oculta, :ocultadaPor)
                    ON CONFLICT (id) DO UPDATE SET oculta = EXCLUDED.oculta, ocultada_por = EXCLUDED.ocultada_por""")
                    .param("id", resena.id())
                    .param("reserva", resena.reservaId())
                    .param("calificacion", resena.calificacion())
                    .param("contenido", resena.contenido())
                    .param("oculta", resena.oculta())
                    .param("ocultadaPor", resena.ocultadaPor())
                    .update();
            return resena;
        } catch (DuplicateKeyException e) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "Ya reseñaste esta reserva.");
        }
    }

    @Override
    public Optional<Resena> buscarPorId(UUID id) {
        return jdbc.sql("SELECT * FROM resena WHERE id = :id")
                .param("id", id)
                .query((fila, numero) -> new Resena(
                        fila.getObject("id", UUID.class),
                        fila.getObject("reserva_id", UUID.class),
                        fila.getInt("calificacion"),
                        fila.getString("contenido"),
                        fila.getBoolean("oculta"),
                        fila.getObject("ocultada_por", UUID.class)))
                .optional();
    }

    @Override
    public Optional<ResenaVisible> buscarVisible(UUID id) {
        return jdbc.sql(VISIBLE_POR_ID)
                .param("id", id)
                .query(RepositorioResenasSql::aVisible)
                .optional();
    }

    @Override
    public List<ResenaVisible> listarVisibles(UUID publicacionId) {
        return jdbc.sql(VISIBLES_DE_PUBLICACION)
                .param("publicacion", publicacionId)
                .query(RepositorioResenasSql::aVisible)
                .list();
    }

    private static ResenaVisible aVisible(ResultSet fila, int numero) throws SQLException {
        return new ResenaVisible(
                fila.getObject("id", UUID.class),
                fila.getString("autor"),
                fila.getInt("calificacion"),
                fila.getString("contenido"),
                fila.getTimestamp("creado_en").toInstant());
    }
}
