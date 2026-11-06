package com.inversioneslr.marketplace.compartido.dominio;

import java.util.UUID;

public record UsuarioAutenticado(UUID id, Rol rol) {
}
