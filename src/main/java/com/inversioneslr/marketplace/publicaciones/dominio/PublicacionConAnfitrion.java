package com.inversioneslr.marketplace.publicaciones.dominio;

import java.util.UUID;

/** La vista sin el nombre del anfitrión, que se pide al módulo de usuarios. */
public record PublicacionConAnfitrion(PublicacionVista vista, UUID anfitrionId) {
}
