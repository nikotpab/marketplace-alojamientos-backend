package com.inversioneslr.marketplace.notificaciones.dominio;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Notificacion(UUID id, UUID usuarioId, String tipo, String mensaje, boolean leida, Instant creadoEn) {

    private static final Map<String, String> MENSAJES = Map.ofEntries(
            Map.entry("ReservaConfirmada", "Tu pago fue aprobado y la reserva quedó confirmada."),
            Map.entry("ReservaCancelada", "Una reserva fue cancelada."),
            Map.entry("ReservaCompletada", "Tu estadía terminó. Ya puedes calificar el alojamiento."),
            Map.entry("ReservaEnRevision", "El alojamiento de tu reserva fue bloqueado y la reserva quedó en revisión."),
            Map.entry("ReservaRestablecida", "El alojamiento de tu reserva fue desbloqueado y la reserva volvió a estar confirmada."),
            Map.entry("DevolucionProcesada", "La devolución de tu reserva fue procesada."),
            Map.entry("DevolucionFallida", "No pudimos procesar la devolución de tu reserva cancelada. La revisaremos."),
            Map.entry("PublicacionAprobada", "Tu publicación fue aprobada y ya está activa."),
            Map.entry("PublicacionRechazada", "Tu publicación fue rechazada. Revisa el motivo."),
            Map.entry("PublicacionBloqueada", "Tu publicación fue bloqueada. Revisa el motivo."),
            Map.entry("PublicacionDesbloqueada", "Tu publicación fue desbloqueada y volvió a estar activa."),
            Map.entry("RestriccionLevantada", "Un administrador levantó la restricción de tu cuenta."),
            Map.entry("CuentaReactivada", "Un administrador reactivó tu cuenta."));

    private static final String MENSAJE_GENERAL = "Hay una novedad en tu cuenta.";

    /** El id sale del evento y del usuario: si el evento se entrega otra vez, es la misma notificación. */
    public static Notificacion nueva(UUID eventoId, UUID usuarioId, String tipo, Instant creadoEn) {
        UUID id = UUID.nameUUIDFromBytes((eventoId + ":" + usuarioId).getBytes(StandardCharsets.UTF_8));
        return new Notificacion(id, usuarioId, tipo, MENSAJES.getOrDefault(tipo, MENSAJE_GENERAL), false, creadoEn);
    }

    public Notificacion marcarLeida() {
        return new Notificacion(id, usuarioId, tipo, mensaje, true, creadoEn);
    }
}
