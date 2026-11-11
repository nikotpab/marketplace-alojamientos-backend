package com.inversioneslr.marketplace.usuarios.dominio;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Tokens de un solo uso que viajan en los enlaces de los correos (SCRUM-109, SCRUM-110). */
public interface TokensCorreo {

    enum Proposito { VERIFICAR_CORREO, RESTABLECER_CONTRASENA }

    /** Invalida el token anterior del mismo propósito y devuelve el nuevo. Solo se guarda su hash. */
    String emitir(UUID usuarioId, Proposito proposito, Instant expira);

    /** El dueño del token, si es válido para ese propósito y no venció. El token deja de servir. */
    Optional<UUID> consumir(String token, Proposito proposito, Instant ahora);

    /** Cuándo se emitió el último token de ese propósito, para no enviar correos seguidos. */
    Optional<Instant> ultimaEmision(UUID usuarioId, Proposito proposito);
}
