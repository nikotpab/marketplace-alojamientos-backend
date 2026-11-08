package com.inversioneslr.marketplace.publicaciones.dominio;

import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Invariantes: capacidad mayor que cero y precio positivo. Solo ACTIVA aparece en búsquedas y recibe reservas. */
public record Publicacion(UUID id, UUID anfitrionId, UUID tipoAlojamientoId, UUID politicaCancelacionId,
                          String titulo, String descripcion, String ciudad, String ubicacionTextual,
                          Integer capacidad, BigDecimal precioNoche, List<UUID> servicios, List<String> reglas,
                          List<String> imagenes, EstadoPublicacion estado, String motivoEstado) {

    /**
     * RF-08: título, descripción, ubicación, tipo, capacidad, precio, al menos una imagen y política.
     * El mensaje de cada campo que falta usa el nombre del campo en el formulario.
     */
    public void exigirInformacionMinima() {
        Map<String, String> faltantes = new LinkedHashMap<>();
        if (vacio(titulo)) {
            faltantes.put("titulo", "Agrega un título para la publicación.");
        }
        if (vacio(descripcion)) {
            faltantes.put("descripcion", "Agrega una descripción del alojamiento.");
        }
        if (vacio(ubicacionTextual)) {
            faltantes.put("ubicacion", "Indica la ubicación del alojamiento.");
        }
        if (tipoAlojamientoId == null) {
            faltantes.put("tipo", "Selecciona el tipo de alojamiento.");
        }
        if (capacidad == null || capacidad < 1) {
            faltantes.put("capacidad", "Indica una capacidad mayor que 0 (mínimo 1 huésped).");
        }
        if (precioNoche == null || precioNoche.signum() <= 0) {
            faltantes.put("precioNoche", "Indica un precio por noche mayor que 0.");
        }
        if (imagenes == null || imagenes.isEmpty()) {
            faltantes.put("imagenes", "Agrega al menos una referencia de imagen (URL).");
        }
        if (politicaCancelacionId == null) {
            faltantes.put("politicaCancelacion", "Describe la política de cancelación.");
        }
        if (!faltantes.isEmpty()) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.INFORMACION_INCOMPLETA,
                    "Falta información para publicar. Completa los campos indicados.", faltantes);
        }
    }

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

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
