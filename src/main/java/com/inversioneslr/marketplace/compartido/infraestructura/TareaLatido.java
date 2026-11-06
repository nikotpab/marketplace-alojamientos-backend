package com.inversioneslr.marketplace.compartido.infraestructura;

import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Latido cada 30 s hacia CloudWatch; si falta o reporta falla, la alarma avisa en máx. 2 min (RNF-15). */
@Component
public class TareaLatido {

    /** Pendiente: publicar el estado de la base de datos, Stripe y el dominio. */
    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
    public void enviar() {
    }
}
