package com.inversioneslr.marketplace.usuarios.infraestructura;

import com.inversioneslr.marketplace.usuarios.dominio.CodigosRegistro;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Tabla codigo_registro (V11). Cada operación es una sola sentencia, sin depender de una transacción. */
@Repository
public class CodigosRegistroSql implements CodigosRegistro {

    private static final String CORREO = "correo";

    private final JdbcClient jdbc;
    private final Clock reloj;

    public CodigosRegistroSql(JdbcClient jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public void guardar(String correo, String hashCodigo, Instant expira) {
        jdbc.sql("""
                INSERT INTO codigo_registro (correo, hash_codigo, intentos, expira_en, creado_en)
                VALUES (:correo, :hash, 0, :expira, :creado)
                ON CONFLICT (correo) DO UPDATE
                SET hash_codigo = excluded.hash_codigo, intentos = 0, expira_en = excluded.expira_en, creado_en = excluded.creado_en""")
                .param(CORREO, correo)
                .param("hash", hashCodigo)
                .param("expira", Timestamp.from(expira))
                .param("creado", Timestamp.from(reloj.instant()))
                .update();
    }

    @Override
    public Optional<Instant> ultimoEnvio(String correo) {
        return jdbc.sql("SELECT creado_en FROM codigo_registro WHERE correo = :correo")
                .param(CORREO, correo)
                .query((fila, numero) -> fila.getTimestamp("creado_en").toInstant())
                .optional();
    }

    @Override
    public Optional<Intento> gastarIntento(String correo, int maximoIntentos, Instant ahora) {
        return jdbc.sql("""
                UPDATE codigo_registro SET intentos = intentos + 1
                WHERE correo = :correo AND expira_en > :ahora AND intentos < :maximo
                RETURNING hash_codigo, intentos""")
                .param(CORREO, correo)
                .param("ahora", Timestamp.from(ahora))
                .param("maximo", maximoIntentos)
                .query((fila, numero) -> new Intento(fila.getString("hash_codigo"), fila.getInt("intentos")))
                .optional();
    }

    @Override
    public boolean usar(String correo, String hashCodigo) {
        return jdbc.sql("DELETE FROM codigo_registro WHERE correo = :correo AND hash_codigo = :hash")
                .param(CORREO, correo)
                .param("hash", hashCodigo)
                .update() == 1;
    }
}
