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
        throw new UnsupportedOperationException("SQL de resena");
    }
}
