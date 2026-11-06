package com.inversioneslr.marketplace.compartido.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnmascaradorLogsTest {

    @Test
    void enmascaraCorreos() {
        assertThat(EnmascaradorLogs.enmascarar("Contacto: laura.gomez+1@correo.com.co")).isEqualTo("Contacto: ***");
    }

    @Test
    void enmascaraTokensDeSesion() {
        assertThat(EnmascaradorLogs.enmascarar("Authorization: Bearer abc.def-ghi")).isEqualTo("Authorization: ***");
    }

    @Test
    void enmascaraClavesDeStripe() {
        assertThat(EnmascaradorLogs.enmascarar("clave sk_ejemplo y pk_ejemplo")).isEqualTo("clave *** y ***");
    }

    @Test
    void enmascaraNumerosDeTarjeta() {
        assertThat(EnmascaradorLogs.enmascarar("tarjeta 4242424242424242")).isEqualTo("tarjeta ***");
    }

    @Test
    void conservaElTextoSinDatosSensibles() {
        assertThat(EnmascaradorLogs.enmascarar("Reserva 123 confirmada")).isEqualTo("Reserva 123 confirmada");
    }

    @Test
    void toleraValoresNulos() {
        assertThat(EnmascaradorLogs.enmascarar(null)).isNull();
    }
}
