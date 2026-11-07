package com.inversioneslr.marketplace.usuarios.dominio;

import java.util.Optional;
import java.util.UUID;

/** Códigos de un solo uso para la recuperación simulada de contraseña (RF-03). */
public interface CodigosRecuperacion {

    String generar(UUID usuarioId);

    /** Devuelve el usuario si el código es válido y lo invalida. */
    Optional<UUID> consumir(String codigo);
}
