package com.inversioneslr.marketplace.resenas.infraestructura;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.resenas.dominio.RepositorioResenas;
import com.inversioneslr.marketplace.resenas.dominio.Resena;
import com.inversioneslr.marketplace.resenas.dominio.ResenaGuardada;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioResenasSql implements RepositorioResenas {

    /** Solo su tabla: el huésped de la reserva y su nombre los dan las fachadas de reservas y usuarios (SCRUM-142). */
    private static final String VISIBLES = "SELECT id, reserva_id, calificacion, contenido, creado_en FROM resena WHERE NOT oculta";

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
    public Optional<ResenaGuardada> buscarVisible(UUID id) {
        return jdbc.sql(VISIBLES + " AND id = :id")
                .param("id", id)
                .query(RepositorioResenasSql::aGuardada)
                .optional();
    }

    @Override
    public List<ResenaGuardada> listarVisibles(Collection<UUID> reservaIds) {
        if (reservaIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(VISIBLES + " AND reserva_id IN (:reservas) ORDER BY creado_en DESC")
                .param("reservas", reservaIds)
                .query(RepositorioResenasSql::aGuardada)
                .list();
    }

    private static ResenaGuardada aGuardada(ResultSet fila, int numero) throws SQLException {
        return new ResenaGuardada(
                fila.getObject("id", UUID.class),
                fila.getObject("reserva_id", UUID.class),
                fila.getInt("calificacion"),
                fila.getString("contenido"),
                fila.getTimestamp("creado_en").toInstant());
    }
}
