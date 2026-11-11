package com.inversioneslr.marketplace.usuarios.infraestructura;

import com.inversioneslr.marketplace.usuarios.dominio.TokensCorreo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 256 bits aleatorios en Base64 URL; en la base queda solo el hash SHA-256 (V9). */
@Repository
public class TokensCorreoSql implements TokensCorreo {

    private final SecureRandom aleatorio = new SecureRandom();
    private final JdbcClient jdbc;
    private final Clock reloj;

    public TokensCorreoSql(JdbcClient jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public String emitir(UUID usuarioId, Proposito proposito, Instant expira) {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.sql("DELETE FROM token_correo WHERE usuario_id = :usuario AND proposito = :proposito")
                .param("usuario", usuarioId)
                .param("proposito", proposito.name())
                .update();
        jdbc.sql("""
                INSERT INTO token_correo (hash_token, usuario_id, proposito, expira_en, creado_en)
                VALUES (:hash, :usuario, :proposito, :expira, :creado)""")
                .param("hash", hash(token))
                .param("usuario", usuarioId)
                .param("proposito", proposito.name())
                .param("expira", Timestamp.from(expira))
                .param("creado", Timestamp.from(reloj.instant()))
                .update();
        return token;
    }

    /** Un solo DELETE: dos solicitudes simultáneas con el mismo token no pueden usarlo las dos. */
    @Override
    public Optional<UUID> consumir(String token, Proposito proposito, Instant ahora) {
        return jdbc.sql("""
                DELETE FROM token_correo
                WHERE hash_token = :hash AND proposito = :proposito AND expira_en > :ahora
                RETURNING usuario_id""")
                .param("hash", hash(token))
                .param("proposito", proposito.name())
                .param("ahora", Timestamp.from(ahora))
                .query(UUID.class)
                .optional();
    }

    @Override
    public Optional<Instant> ultimaEmision(UUID usuarioId, Proposito proposito) {
        return jdbc.sql("""
                SELECT creado_en FROM token_correo
                WHERE usuario_id = :usuario AND proposito = :proposito
                ORDER BY creado_en DESC LIMIT 1""")
                .param("usuario", usuarioId)
                .param("proposito", proposito.name())
                .query((fila, numero) -> fila.getTimestamp("creado_en").toInstant())
                .optional();
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }
}
