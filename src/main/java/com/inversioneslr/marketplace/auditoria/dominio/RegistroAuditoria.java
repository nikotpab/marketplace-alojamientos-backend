package com.inversioneslr.marketplace.auditoria.dominio;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Fila de solo inserción. El hash permite detectar si alguien la altera directamente en la base de datos. */
public record RegistroAuditoria(UUID id, UUID actorId, String accion, String entidad, UUID entidadId, String resultado,
                                Map<String, Object> detalle, String hash, Instant fecha) {

    public static RegistroAuditoria de(EventoDominio evento, Instant instante) {
        // PostgreSQL guarda microsegundos: truncar aquí mantiene verificable el hash.
        Instant fecha = instante.truncatedTo(ChronoUnit.MICROS);
        Map<String, Object> detalle = evento.detalle() == null ? Map.of() : evento.detalle();
        return new RegistroAuditoria(UUID.randomUUID(), evento.actorId(), evento.tipo(), evento.entidad(), evento.entidadId(),
                evento.resultado(), detalle,
                hash(evento.actorId(), evento.tipo(), evento.entidad(), evento.entidadId(), evento.resultado(), detalle, fecha), fecha);
    }

    /** ASR-07: false si alguien cambió la fila directamente en la base de datos, saltándose los triggers. */
    public boolean integro() {
        return hash.equals(hash(actorId, accion, entidad, entidadId, resultado, detalle, fecha));
    }

    private static String hash(UUID actorId, String accion, String entidad, UUID entidadId, String resultado,
                               Map<String, Object> detalle, Instant fecha) {
        return sha256(String.join("|", String.valueOf(actorId), accion, entidad, String.valueOf(entidadId), resultado,
                new TreeMap<>(detalle).toString(), fecha.toString()));
    }

    private static String sha256(String contenido) {
        try {
            byte[] huella = MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(huella);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM debe incluir SHA-256", e);
        }
    }
}
