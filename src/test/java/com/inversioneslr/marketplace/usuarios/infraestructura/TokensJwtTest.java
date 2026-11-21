package com.inversioneslr.marketplace.usuarios.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import com.inversioneslr.marketplace.usuarios.dominio.RepositorioUsuarios;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokensJwtTest {

    private static final String SECRETO = "a".repeat(32);
    private static final Instant AHORA = Instant.parse("2030-01-01T12:00:00Z");

    private final UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), Rol.HUESPED);
    private final RepositorioUsuarios usuarios = mock(RepositorioUsuarios.class);

    @BeforeEach
    void preparar() {
        when(usuarios.versionSesionVigente(any())).thenReturn(Optional.of(0));
    }

    @Test
    void unTokenEmitidoIdentificaAlUsuarioYSuRol() {
        TokensJwt tokens = tokensEn(AHORA);

        assertThat(tokens.verificar(tokens.emitir(usuario))).contains(usuario);
    }

    /** Restringir, desactivar o restablecer la contraseña sube la versión de sesión. */
    @Test
    void unTokenDeUnaVersionAnteriorNoEsValido() {
        TokensJwt tokens = tokensEn(AHORA);
        String token = tokens.emitir(usuario);

        when(usuarios.versionSesionVigente(usuario.id())).thenReturn(Optional.of(1));

        assertThat(tokens.verificar(token)).isEmpty();
        assertThat(tokens.verificar(tokens.emitir(usuario))).contains(usuario);
    }

    /** El repositorio no da versión si la cuenta no existe, está desactivada o restringida. */
    @Test
    void elTokenDeUnaCuentaQueNoPuedeOperarNoEsValido() {
        TokensJwt tokens = tokensEn(AHORA);
        String token = tokens.emitir(usuario);

        when(usuarios.versionSesionVigente(usuario.id())).thenReturn(Optional.empty());

        assertThat(tokens.verificar(token)).isEmpty();
    }

    @Test
    void unTokenVencidoNoEsValido() {
        String token = tokensEn(AHORA).emitir(usuario);

        assertThat(tokensEn(AHORA.plus(Duration.ofHours(9))).verificar(token)).isEmpty();
    }

    @Test
    void unTokenFirmadoConOtraClaveNoEsValido() {
        String ajeno = new TokensJwt("b".repeat(32), Clock.fixed(AHORA, ZoneOffset.UTC), usuarios).emitir(usuario);

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
        Clock reloj = Clock.systemUTC();

        assertThatThrownBy(() -> new TokensJwt("corta", reloj, usuarios)).isInstanceOf(IllegalArgumentException.class);
    }

    private TokensJwt tokensEn(Instant instante) {
        return new TokensJwt(SECRETO, Clock.fixed(instante, ZoneOffset.UTC), usuarios);
    }
}
