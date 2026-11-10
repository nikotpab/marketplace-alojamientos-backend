package com.inversioneslr.marketplace.compartido.presentacion;

import com.inversioneslr.marketplace.compartido.Tokens;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Primer eslabón de la cadena (Chain of Responsibility): identifica al usuario por su token. */
class FiltroAutenticacion extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final Tokens tokens;

    FiltroAutenticacion(Tokens tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String cabecera = solicitud.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith(PREFIJO)) {
            tokens.verificar(cabecera.substring(PREFIJO.length())).ifPresent(usuario ->
                    SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(usuario, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol())))));
        }
        cadena.doFilter(solicitud, respuesta);
    }
}
