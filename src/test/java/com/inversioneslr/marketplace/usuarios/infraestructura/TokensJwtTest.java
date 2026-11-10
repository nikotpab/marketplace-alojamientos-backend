package com.inversioneslr.marketplace.usuarios.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokensJwtTest {

    private static final String SECRETO = "a".repeat(32);
    private static final Instant AHORA = Instant.parse("2030-01-01T12:00:00Z");

    private final UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), Rol.HUESPED);

    @Test
    void unTokenEmitidoIdentificaAlUsuarioYSuRol() {
        TokensJwt tokens = tokensEn(AHORA);

        assertThat(tokens.verificar(tokens.emitir(usuario))).contains(usuario);
    }

    @Test
    void unTokenVencidoNoEsValido() {
        String token = tokensEn(AHORA).emitir(usuario);

        assertThat(tokensEn(AHORA.plus(Duration.ofHours(9))).verificar(token)).isEmpty();
    }

    @Test
    void unTokenFirmadoConOtraClaveNoEsValido() {
        String ajeno = new TokensJwt("b".repeat(32), Clock.fixed(AHORA, ZoneOffset.UTC)).emitir(usuario);

        assertThat(tokensEn(AHORA).verificar(ajeno)).isEmpty();
    }

    @Test
    void unTokenAlteradoNoEsValido() {
        String token = tokensEn(AHORA).emitir(usuario);
        String alterado = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThat(tokensEn(AHORA).verificar(alterado)).isEmpty();
        assertThat(tokensEn(AHORA).verificar("no-es-un-token")).isEmpty();
    }

    @Test
    void exigeUnaClaveDeAlMenos32Caracteres() {
        assertThatThrownBy(() -> new TokensJwt("corta", Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }

    private static TokensJwt tokensEn(Instant instante) {
        return new TokensJwt(SECRETO, Clock.fixed(instante, ZoneOffset.UTC));
    }
}
