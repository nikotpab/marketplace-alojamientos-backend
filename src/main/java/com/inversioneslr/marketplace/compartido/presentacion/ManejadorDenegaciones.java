package com.inversioneslr.marketplace.compartido.presentacion;

import com.inversioneslr.marketplace.compartido.AccesoDenegado;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
        AccesoDenegado denegacion = new AccesoDenegado(actorActual(), solicitud.getMethod(), solicitud.getRequestURI());
        try {
            eventos.publicar(denegacion);
        } catch (RuntimeException e) {
            // Una falla al registrar no puede convertir la denegación en otra respuesta.
            log.error("No se pudo registrar la denegación {}", denegacion.detalle(), e);
        }
    }

    private UUID actorActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario
                ? usuario.id()
                : null;
    }
}
