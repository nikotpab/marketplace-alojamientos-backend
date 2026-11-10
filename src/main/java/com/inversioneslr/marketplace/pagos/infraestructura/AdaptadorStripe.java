package com.inversioneslr.marketplace.pagos.infraestructura;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Único lugar con código de Stripe (ADR-08). */
class AdaptadorStripe implements PasarelaPagos {

    private static final Logger log = LoggerFactory.getLogger(AdaptadorStripe.class);

    /** Conexión y lectura suman los 7 s de RNF-11. Sin reintentos del SDK, que alargarían la espera. */
    private static final int TIEMPO_CONEXION_MS = 2_000;
    private static final int TIEMPO_LECTURA_MS = 5_000;
    private static final String MONEDA = "cop";

    private final StripeClient stripe;
    private final String metodoPago;

    AdaptadorStripe(StripeClient stripe, String metodoPago) {
        this.stripe = stripe;
        this.metodoPago = metodoPago;
    }

    static StripeClient.StripeClientBuilder cliente(String claveSecreta) {
        return StripeClient.builder()
                .setApiKey(claveSecreta)
                .setConnectTimeout(TIEMPO_CONEXION_MS)
                .setReadTimeout(TIEMPO_LECTURA_MS)
                .setMaxNetworkRetries(0);
    }

    @Override
    public String cobrar(BigDecimal monto, String clave) {
        PaymentIntentCreateParams parametros = PaymentIntentCreateParams.builder()
                .setAmount(enCentavos(monto))
                .setCurrency(MONEDA)
                .setPaymentMethod(metodoPago)
                .setConfirm(true)
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build())
                .build();
        try {
            PaymentIntent pago = stripe.v1().paymentIntents().create(parametros, conClave(clave));
            if (!"succeeded".equals(pago.getStatus())) {
                throw pagoRechazado();
            }
            return pago.getId();
        } catch (CardException e) {
            throw pagoRechazado();
        } catch (StripeException e) {
            log.warn("Stripe no procesó el cobro: {} {}", e.getStatusCode(), e.getCode());
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO, "El pago no se procesó, intenta de nuevo.");
        }
    }

    @Override
    public void devolver(String referencia, BigDecimal monto) {
        RefundCreateParams parametros = RefundCreateParams.builder()
                .setPaymentIntent(referencia)
                .setAmount(enCentavos(monto))
                .build();
        try {
            stripe.v1().refunds().create(parametros, conClave("devolucion-" + referencia));
        } catch (StripeException e) {
            log.warn("Stripe no procesó la devolución: {} {}", e.getStatusCode(), e.getCode());
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO, "La devolución no se procesó, intenta de nuevo.");
        }
    }

    /** COP es una moneda de dos decimales para Stripe: el monto va en centavos. */
    private static long enCentavos(BigDecimal monto) {
        return monto.movePointRight(2).longValueExact();
    }

    private static RequestOptions conClave(String clave) {
        return RequestOptions.builder().setIdempotencyKey(clave).build();
    }

    private static ExcepcionNegocio pagoRechazado() {
        return new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_RECHAZADO, "El pago no fue aprobado.");
    }
}
