package com.inversioneslr.marketplace.usuarios.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CodigosRecuperacionEnMemoriaTest {

    private static final Instant AHORA = Instant.parse("2030-01-01T12:00:00Z");

    private final Clock reloj = mock(Clock.class);
    private final CodigosRecuperacionEnMemoria codigos = new CodigosRecuperacionEnMemoria(reloj);
    private final UUID usuario = UUID.randomUUID();

    @Test
    void unCodigoSirveUnaSolaVez() {
        when(reloj.instant()).thenReturn(AHORA);
        String codigo = codigos.generar(usuario);

        assertThat(codigos.consumir(codigo)).contains(usuario);
        assertThat(codigos.consumir(codigo)).isEmpty();
    }

    @Test
    void unCodigoVenceALos15Minutos() {
        when(reloj.instant()).thenReturn(AHORA);
        String codigo = codigos.generar(usuario);

        when(reloj.instant()).thenReturn(AHORA.plus(Duration.ofMinutes(16)));

        assertThat(codigos.consumir(codigo)).isEmpty();
    }

    @Test
    void unCodigoDesconocidoNoEsValido() {
        when(reloj.instant()).thenReturn(AHORA);

        assertThat(codigos.consumir("inventado")).isEmpty();
    }
}
