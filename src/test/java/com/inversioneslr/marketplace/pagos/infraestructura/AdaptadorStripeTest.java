package com.inversioneslr.marketplace.pagos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Usa un servidor local en lugar de Stripe para simular sus respuestas y su lentitud. */
class AdaptadorStripeTest {

    private static final String PAGO_APROBADO = "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}";
    private static final String TARJETA_RECHAZADA = "{\"error\":{\"type\":\"card_error\",\"code\":\"card_declined\",\"message\":\"rechazada\"}}";

    private final AtomicReference<String> clave = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();

    private HttpServer servidor;
    private AdaptadorStripe adaptador;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.start();
        String base = "http://localhost:" + servidor.getAddress().getPort();
        adaptador = new AdaptadorStripe(AdaptadorStripe.cliente("clave-de-prueba").setApiBase(base).build(), "pm_card_visa");
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    @Test
    void cobraEnCentavosConLaClaveDeIdempotencia() {
        responder("/v1/payment_intents", 200, PAGO_APROBADO);

        String referencia = adaptador.cobrar(new BigDecimal("1062000.00"), "clave-1");

        assertThat(referencia).isEqualTo("pi_1");
        assertThat(clave.get()).isEqualTo("clave-1");
        assertThat(cuerpo.get()).contains("amount=106200000", "currency=cop", "confirm=true", "payment_method=pm_card_visa");
    }

    @Test
    void tarjetaRechazadaEsPagoRechazado() {
        responder("/v1/payment_intents", 402, TARJETA_RECHAZADA);

        assertThatThrownBy(() -> adaptador.cobrar(BigDecimal.TEN, "clave-2"))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.PAGO_RECHAZADO));
    }

    @Test
    void errorDelProveedorEsPagoNoProcesado() {
        responder("/v1/payment_intents", 500, "{\"error\":{\"type\":\"api_error\",\"message\":\"caido\"}}");

        assertThatThrownBy(() -> adaptador.cobrar(BigDecimal.TEN, "clave-3"))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO));
    }

    @Test
    void sinRespuestaDelProveedorCortaAntesDeSieteSegundos() {
        servidor.createContext("/v1/payment_intents", intercambio -> {
            try {
                Thread.sleep(Duration.ofSeconds(8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            intercambio.close();
        });
        Instant inicio = Instant.now();

        assertThatThrownBy(() -> adaptador.cobrar(BigDecimal.TEN, "clave-4"))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO));

        assertThat(Duration.between(inicio, Instant.now())).isLessThanOrEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void devuelveElMontoDelPago() {
        responder("/v1/refunds", 200, "{\"id\":\"re_1\",\"object\":\"refund\",\"status\":\"succeeded\"}");

        adaptador.devolver("pi_1", new BigDecimal("978000.00"));

        assertThat(cuerpo.get()).contains("payment_intent=pi_1", "amount=97800000");
        assertThat(clave.get()).isEqualTo("devolucion-pi_1");
    }

    @Test
    void devolucionFallidaSeInforma() {
        responder("/v1/refunds", 500, "{\"error\":{\"type\":\"api_error\",\"message\":\"caido\"}}");

        assertThatThrownBy(() -> adaptador.devolver("pi_1", BigDecimal.TEN))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        error -> assertThat(error.tipo()).isEqualTo(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO));
    }

    private void responder(String ruta, int estado, String respuesta) {
        servidor.createContext(ruta, intercambio -> {
            clave.set(intercambio.getRequestHeaders().getFirst("Idempotency-Key"));
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] contenido = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, contenido.length);
            intercambio.getResponseBody().write(contenido);
            intercambio.close();
        });
    }
}
