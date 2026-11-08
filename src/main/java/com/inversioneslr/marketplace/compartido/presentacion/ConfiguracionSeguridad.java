package com.inversioneslr.marketplace.compartido.presentacion;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.inversioneslr.marketplace.compartido.dominio.Tokens;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Punto único de autorización (ADR-07): la cadena autentica con {@link FiltroAutenticacion}
 * y autoriza con el AuthorizationFilter de Spring según esta tabla. Ruta sin declarar, ruta denegada.
 * La propiedad del recurso se verifica en cada caso de uso.
 */
@Configuration
public class ConfiguracionSeguridad {

    private static final String HUESPED = Rol.HUESPED.name();
    private static final String ANFITRION = Rol.ANFITRION.name();
    private static final String ADMINISTRADOR = Rol.ADMINISTRADOR.name();

    @Bean
    SecurityFilterChain cadenaDeFiltros(HttpSecurity http, Tokens tokens, ManejadorDenegaciones denegaciones) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new FiltroAutenticacion(tokens), AuthorizationFilter.class)
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(denegaciones))
                .authorizeHttpRequests(rutas -> rutas
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()

                        // Sin sesión
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/alojamientos/**", "/api/v1/catalogos", "/api/v1/configuracion/tipos-alojamiento",
                                "/api/v1/publicaciones/*/disponibilidad").permitAll()

                        // Administrador
                        .requestMatchers("/api/v1/admin/**").hasRole(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/publicaciones/*/revision").hasRole(ADMINISTRADOR)

                        // Anfitrión
                        .requestMatchers(HttpMethod.POST, "/api/v1/publicaciones",
                                "/api/v1/publicaciones/*/solicitar-activacion",
                                "/api/v1/publicaciones/*/pausar",
                                "/api/v1/publicaciones/*/reactivar").hasRole(ANFITRION)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/publicaciones/*/disponibilidad").hasRole(ANFITRION)
                        .requestMatchers(HttpMethod.GET, "/api/v1/publicaciones/mias").hasRole(ANFITRION)
                        // Después de /mias, que también encaja en /publicaciones/*
                        .requestMatchers(HttpMethod.GET, "/api/v1/publicaciones/*").hasAnyRole(ANFITRION, ADMINISTRADOR)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/publicaciones/*").hasAnyRole(ANFITRION, ADMINISTRADOR)

                        // Huésped
                        .requestMatchers(HttpMethod.POST, "/api/v1/cotizaciones", "/api/v1/reservas",
                                "/api/v1/reservas/*/cancelacion", "/api/v1/reservas/*/resena").hasRole(HUESPED)
                        .requestMatchers(HttpMethod.GET, "/api/v1/reservas/*/cancelacion/estimacion").hasRole(HUESPED)
                        .requestMatchers(HttpMethod.GET, "/api/v1/reservas").hasAnyRole(HUESPED, ANFITRION)

                        // Cualquier usuario con sesión
                        .requestMatchers("/api/v1/usuarios/me/**", "/api/v1/notificaciones/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/publicaciones/*/resenas").authenticated()

                        .anyRequest().denyAll())
                .build();
    }

    /** Hash con salt único por usuario (RNF-19). */
    @Bean
    PasswordEncoder cifradorContrasenas() {
        return new BCryptPasswordEncoder();
    }
}
