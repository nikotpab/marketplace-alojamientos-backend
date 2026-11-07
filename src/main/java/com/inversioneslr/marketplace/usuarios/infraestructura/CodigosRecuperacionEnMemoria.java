package com.inversioneslr.marketplace.usuarios.infraestructura;

import com.inversioneslr.marketplace.usuarios.dominio.CodigosRecuperacion;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * En memoria: basta para la recuperación simulada con una sola instancia, pero los códigos
 * se pierden al reiniciar (decisión pendiente 7 del README).
 */
@Component
public class CodigosRecuperacionEnMemoria implements CodigosRecuperacion {

    private static final Duration VIGENCIA = Duration.ofMinutes(15);

    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, Pendiente> pendientes = new ConcurrentHashMap<>();
    private final Clock reloj;

    public CodigosRecuperacionEnMemoria(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public String generar(UUID usuarioId) {
        Instant ahora = reloj.instant();
        pendientes.values().removeIf(pendiente -> !pendiente.vence().isAfter(ahora));
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendientes.put(codigo, new Pendiente(usuarioId, ahora.plus(VIGENCIA)));
        return codigo;
    }

    @Override
    public Optional<UUID> consumir(String codigo) {
        Pendiente pendiente = pendientes.remove(codigo);
        return pendiente != null && pendiente.vence().isAfter(reloj.instant())
                ? Optional.of(pendiente.usuarioId())
                : Optional.empty();
    }

    private record Pendiente(UUID usuarioId, Instant vence) {
    }
}
