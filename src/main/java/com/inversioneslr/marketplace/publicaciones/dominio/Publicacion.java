package com.inversioneslr.marketplace.publicaciones.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Invariantes: capacidad mayor que cero y precio positivo. Solo ACTIVA aparece en búsquedas y recibe reservas. */
public record Publicacion(UUID id, UUID anfitrionId, UUID tipoAlojamientoId, UUID politicaCancelacionId,
                          String titulo, String descripcion, String ciudad, String ubicacionTextual,
                          Integer capacidad, BigDecimal precioNoche, List<UUID> servicios, List<String> reglas,
                          List<String> imagenes, EstadoPublicacion estado, String motivoEstado) {

    public Publicacion solicitarActivacion() {
        throw new UnsupportedOperationException("RF-08");
    }

    public Publicacion aprobar() {
        throw new UnsupportedOperationException("RF-09");
    }

    public Publicacion rechazar(String motivo) {
        throw new UnsupportedOperationException("RF-09");
    }

    public Publicacion pausar() {
        throw new UnsupportedOperationException("RF-10");
    }

    public Publicacion bloquear(String motivo) {
        throw new UnsupportedOperationException("RF-27");
    }
}
