package com.inversioneslr.marketplace.compartido.presentacion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Agrega un correlationId a los logs de cada petición (RNF-18). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroCorrelacion extends OncePerRequestFilter {

    private static final String CLAVE_LOG = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CLAVE_LOG, correlationId);
        respuesta.setHeader("X-Correlation-Id", correlationId);
        try {
            cadena.doFilter(solicitud, respuesta);
        } finally {
            MDC.remove(CLAVE_LOG);
        }
    }
}
