package com.inversioneslr.marketplace.usuarios.dominio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioUsuarios {

    Optional<Usuario> buscarPorId(UUID id);

    Optional<Usuario> buscarPorCorreo(String correo);

    List<Usuario> buscarPorIds(Collection<UUID> ids);

    List<Usuario> listar();

    Usuario guardar(Usuario usuario);
}
