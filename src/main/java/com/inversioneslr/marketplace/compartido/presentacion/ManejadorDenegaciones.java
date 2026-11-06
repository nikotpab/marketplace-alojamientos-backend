package com.inversioneslr.marketplace.compartido.presentacion;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Cada denegación devuelve 403 y queda registrada en auditoría (ADR-07). */
@Component
public class ManejadorDenegaciones implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDenegaciones.class);

    private final PublicadorEventos eventos;

    public ManejadorDenegaciones(PublicadorEventos eventos) {
        this.eventos = eventos;
    }

    @Override
    public void handle(HttpServletRequest solicitud, HttpServletResponse respuesta, AccessDeniedException excepcion) {
        registrar(solicitud);
        respuesta.setStatus(HttpStatus.FORBIDDEN.value());
    }

    void registrar(HttpServletRequest solicitud) {
        Map<String, Object> detalle = Map.of("metodo", solicitud.getMethod(), "ruta", solicitud.getRequestURI());
        try {
            eventos.publicar(new EventoDominio("AccesoDenegado", actorActual(), "ruta", null, "DENEGADO", detalle, List.of()));
        } catch (RuntimeException e) {
            // Una falla al registrar no puede convertir la denegación en otra respuesta.
            log.error("No se pudo registrar la denegación {}", detalle, e);
        }
    }

    private UUID actorActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario
                ? usuario.id()
                : null;
    }
}
