package com.inversioneslr.marketplace.publicaciones.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La publicación como la muestra el frontend: tipo por código, servicios por nombre y política por nombre.
 *
 * @param motivoRechazo motivo del último rechazo, solo si volvió a BORRADOR
 * @param motivoBloqueo motivo del bloqueo, solo si está BLOQUEADA
 * @param anfitrion     nombre del anfitrión, para la vista del administrador
 */
public record PublicacionVista(UUID id, EstadoPublicacion estado, Instant actualizadaEn, String motivoRechazo, String motivoBloqueo,
                               String anfitrion,
                               String titulo, String descripcion, String ubicacion, String tipo, Integer capacidad,
                               BigDecimal precioNoche, List<String> servicios, List<String> reglas, List<String> imagenes,
                               String politicaCancelacion) {

    public PublicacionVista conAnfitrion(String nombre) {
        return new PublicacionVista(id, estado, actualizadaEn, motivoRechazo, motivoBloqueo, nombre, titulo, descripcion, ubicacion,
                tipo, capacidad, precioNoche, servicios, reglas, imagenes, politicaCancelacion);
    }
}
