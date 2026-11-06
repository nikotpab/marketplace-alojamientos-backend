package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.time.Duration;

/** Decorator (ADR-05): abre tras 5 fallas seguidas, responde de inmediato y prueba de nuevo a los 30 s. */
class CircuitBreakerPagos implements PasarelaPagos {

    private static final int FALLAS_SEGUIDAS = 5;
    private static final Duration ESPERA_ABIERTO = Duration.ofSeconds(30);

    private final PasarelaPagos pasarela;
    private final CircuitBreaker circuito;

    CircuitBreakerPagos(PasarelaPagos pasarela) {
        this(pasarela, ESPERA_ABIERTO);
    }

    CircuitBreakerPagos(PasarelaPagos pasarela, Duration esperaAbierto) {
        this.pasarela = pasarela;
        this.circuito = CircuitBreaker.of("pagos", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(FALLAS_SEGUIDAS)
                .minimumNumberOfCalls(FALLAS_SEGUIDAS)
                .failureRateThreshold(100)
                .waitDurationInOpenState(esperaAbierto)
                .permittedNumberOfCallsInHalfOpenState(1)
                .ignoreException(CircuitBreakerPagos::esPagoRechazado)
                .build());
    }

    @Override
    public String cobrar(BigDecimal monto, String clave) {
        try {
            return circuito.executeSupplier(() -> pasarela.cobrar(monto, clave));
        } catch (CallNotPermittedException e) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO, "El pago no se procesó, intenta de nuevo");
        }
    }

    @Override
    public void devolver(String referencia, BigDecimal monto) {
        try {
            circuito.executeRunnable(() -> pasarela.devolver(referencia, monto));
        } catch (CallNotPermittedException e) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO, "La devolución no se procesó, intenta de nuevo");
        }
    }

    /** Un rechazo es una respuesta válida de la pasarela: no cuenta como falla (SEC-02). */
    private static boolean esPagoRechazado(Throwable error) {
        return error instanceof ExcepcionNegocio negocio && negocio.tipo() == ExcepcionNegocio.Tipo.PAGO_RECHAZADO;
    }
}
