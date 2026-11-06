package com.inversioneslr.marketplace.compartido.infraestructura;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionReloj {

    /** Las fechas de negocio son de Colombia, aunque el servidor corra en us-east-1. */
    @Bean
    Clock reloj() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }
}
