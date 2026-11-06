package com.inversioneslr.marketplace.usuarios.infraestructura;

import com.inversioneslr.marketplace.usuarios.dominio.RepositorioUsuarios;
import com.inversioneslr.marketplace.usuarios.dominio.Usuario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioUsuariosSql implements RepositorioUsuarios {

    private final JdbcClient jdbc;

    public RepositorioUsuariosSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        throw new UnsupportedOperationException("SQL de usuario");
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        throw new UnsupportedOperationException("SQL de usuario");
    }

    @Override
    public List<Usuario> listar() {
        throw new UnsupportedOperationException("SQL de usuario");
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        throw new UnsupportedOperationException("SQL de usuario");
    }
}
