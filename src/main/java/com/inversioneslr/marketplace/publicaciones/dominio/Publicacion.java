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

    /** RF-08: solo un borrador completo pasa a revisión. Se borra el motivo del rechazo anterior. */
    public Publicacion solicitarActivacion() {
        if (estado != EstadoPublicacion.BORRADOR) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "Solo un borrador puede enviarse a revisión.");
        }
        exigirInformacionMinima();
        return conEstado(EstadoPublicacion.PENDIENTE_REVISION, null);
    }

    /** RF-09: solo una publicación pendiente de revisión pasa a ACTIVA. */
    public Publicacion aprobar() {
        exigirPendienteDeRevision();
        return conEstado(EstadoPublicacion.ACTIVA, null);
    }

    /** RF-09: vuelve a BORRADOR con el motivo, que es obligatorio, para que el anfitrión sepa qué corregir. */
    public Publicacion rechazar(String motivo) {
        exigirPendienteDeRevision();
        if (vacio(motivo)) {
            String mensaje = "Indica el motivo del rechazo para que el anfitrión sepa qué corregir.";
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, mensaje, Map.of("motivo", mensaje));
        }
        return conEstado(EstadoPublicacion.BORRADOR, motivo.trim());
    }

    private void exigirPendienteDeRevision() {
        if (estado != EstadoPublicacion.PENDIENTE_REVISION) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO,
                    "La publicación ya no está pendiente de revisión: su estado es " + estado + ".");
        }
    }

    /** RF-10: deja de aparecer en la búsqueda y de recibir reservas. */
    public Publicacion pausar() {
        if (estado != EstadoPublicacion.ACTIVA) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "Solo una publicación activa puede pausarse.");
        }
        return conEstado(EstadoPublicacion.PAUSADA, null);
    }

    /** Decisión temporal: vuelve a ACTIVA sin pasar por revisión (pendiente 10 del README). */
    public Publicacion reactivar() {
        if (estado == EstadoPublicacion.BLOQUEADA) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "Una publicación bloqueada solo la puede desbloquear un administrador.");
        }
        if (estado != EstadoPublicacion.PAUSADA) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "Solo una publicación pausada puede reactivarse.");
        }
        return conEstado(EstadoPublicacion.ACTIVA, null);
    }

    public Publicacion bloquear(String motivo) {
        throw new UnsupportedOperationException("RF-27");
    }

    private Publicacion conEstado(EstadoPublicacion nuevo, String motivo) {
        return new Publicacion(id, anfitrionId, tipoAlojamientoId, politicaCancelacionId, titulo, descripcion, ciudad,
                ubicacionTextual, capacidad, precioNoche, servicios, reglas, imagenes, nuevo, motivo);
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
