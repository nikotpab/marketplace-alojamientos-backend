package com.inversioneslr.marketplace.publicaciones.dominio;

import java.util.UUID;

/** Política elegible al publicar. Sus reglas de devolución viven en el módulo cancelaciones. */
public record PoliticaOfrecida(UUID id, String nombre) {
}
