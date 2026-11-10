package com.inversioneslr.marketplace.notificaciones.dominio;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Notificacion(UUID id, UUID usuarioId, String tipo, String mensaje, boolean leida, Instant creadoEn) {

    private static final Map<String, String> MENSAJES = Map.of(
            "ReservaConfirmada", "Tu pago fue aprobado y la reserva quedó confirmada.",
            "ReservaCancelada", "Una reserva fue cancelada.",
            "DevolucionProcesada", "La devolución de tu reserva fue procesada.",
            "DevolucionFallida", "No pudimos procesar la devolución de tu reserva cancelada. La revisaremos.",
            "PublicacionAprobada", "Tu publicación fue aprobada y ya está activa.",
            "PublicacionRechazada", "Tu publicación fue rechazada. Revisa el motivo.",
            "PublicacionBloqueada", "Tu publicación fue bloqueada. Revisa el motivo.");

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
