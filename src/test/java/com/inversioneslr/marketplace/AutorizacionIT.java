package com.inversioneslr.marketplace;

import static com.inversioneslr.marketplace.compartido.Rol.ADMINISTRADOR;
import static com.inversioneslr.marketplace.compartido.Rol.ANFITRION;
import static com.inversioneslr.marketplace.compartido.Rol.HUESPED;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * SCRUM-64 · ASR-06 · ADR-07: recorre el 100 % de los endpoints con cada rol. Un rol sin permiso recibe 403 y el
 * intento queda en la auditoría; sin sesión, 401. La tabla debe coincidir con la de ConfiguracionSeguridad.
 */
class AutorizacionIT extends PruebaIntegracion {

    private static final Set<Rol> SOLO_ADMINISTRADOR = EnumSet.of(ADMINISTRADOR);
    private static final Set<Rol> SOLO_ANFITRION = EnumSet.of(ANFITRION);
    private static final Set<Rol> SOLO_HUESPED = EnumSet.of(HUESPED);
    private static final Set<Rol> CUALQUIER_ROL = EnumSet.allOf(Rol.class);

    /** Quién puede usar cada endpoint que exige sesión. */
    private static final Map<String, Set<Rol>> CON_SESION = Map.ofEntries(
            entry("GET /api/v1/admin/auditoria", SOLO_ADMINISTRADOR),
            entry("GET /api/v1/admin/dashboard", SOLO_ADMINISTRADOR),
            entry("GET /api/v1/admin/publicaciones", SOLO_ADMINISTRADOR),
            entry("POST /api/v1/admin/publicaciones/{id}/aprobar", SOLO_ADMINISTRADOR),
            entry("POST /api/v1/admin/publicaciones/{id}/bloqueo", SOLO_ADMINISTRADOR),
            entry("POST /api/v1/admin/publicaciones/{id}/rechazar", SOLO_ADMINISTRADOR),
            entry("GET /api/v1/admin/reportes", SOLO_ADMINISTRADOR),
            entry("POST /api/v1/admin/resenas/{id}/ocultamiento", SOLO_ADMINISTRADOR),
            entry("GET /api/v1/admin/tarifas", SOLO_ADMINISTRADOR),
            entry("PUT /api/v1/admin/tarifas", SOLO_ADMINISTRADOR),
            entry("GET /api/v1/admin/tipos-alojamiento", SOLO_ADMINISTRADOR),
            entry("POST /api/v1/admin/tipos-alojamiento", SOLO_ADMINISTRADOR),
            entry("PUT /api/v1/admin/tipos-alojamiento/{id}", SOLO_ADMINISTRADOR),
            entry("GET /api/v1/admin/usuarios", SOLO_ADMINISTRADOR),
            entry("POST /api/v1/admin/usuarios/{id}/restriccion", SOLO_ADMINISTRADOR),
            entry("POST /api/v1/publicaciones", SOLO_ANFITRION),
            entry("POST /api/v1/publicaciones/{id}/solicitar-activacion", SOLO_ANFITRION),
            entry("POST /api/v1/publicaciones/{id}/pausar", SOLO_ANFITRION),
            entry("POST /api/v1/publicaciones/{id}/reactivar", SOLO_ANFITRION),
            entry("PUT /api/v1/publicaciones/{id}/disponibilidad", SOLO_ANFITRION),
            entry("GET /api/v1/publicaciones/mias", SOLO_ANFITRION),
            entry("GET /api/v1/publicaciones/{id}", EnumSet.of(ANFITRION, ADMINISTRADOR)),
            entry("PUT /api/v1/publicaciones/{id}", EnumSet.of(ANFITRION, ADMINISTRADOR)),
            entry("POST /api/v1/cotizaciones", SOLO_HUESPED),
            entry("POST /api/v1/reservas", SOLO_HUESPED),
            entry("POST /api/v1/reservas/{reservaId}/cancelacion", SOLO_HUESPED),
            entry("GET /api/v1/reservas/{reservaId}/cancelacion/estimacion", SOLO_HUESPED),
            entry("POST /api/v1/reservas/{reservaId}/resena", SOLO_HUESPED),
            entry("GET /api/v1/reservas", EnumSet.of(HUESPED, ANFITRION)),
            entry("GET /api/v1/usuarios/me", CUALQUIER_ROL),
            entry("PUT /api/v1/usuarios/me", CUALQUIER_ROL),
            entry("PATCH /api/v1/usuarios/me/estado", CUALQUIER_ROL),
            entry("GET /api/v1/notificaciones", CUALQUIER_ROL),
            entry("POST /api/v1/notificaciones/{id}/lectura", CUALQUIER_ROL),
            entry("POST /api/v1/notificaciones/lectura", CUALQUIER_ROL),
            entry("GET /api/v1/publicaciones/{publicacionId}/resenas", CUALQUIER_ROL));

    /** No exigen sesión. */
    private static final Set<String> PUBLICOS = Set.of(
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/password/recover",
            "POST /api/v1/auth/password/reset",
            "POST /api/v1/auth/email/verify",
            "POST /api/v1/auth/email/resend",
            "GET /api/v1/alojamientos",
            "GET /api/v1/alojamientos/{id}",
            "GET /api/v1/catalogos",
            "GET /api/v1/configuracion/tipos-alojamiento",
            "GET /api/v1/publicaciones/{id}/disponibilidad");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping rutas;

    @Test
    void laTablaCubreTodosLosEndpoints() {
        Set<String> declarados = rutas.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getMethodsCondition().getMethods().stream()
                        .flatMap(metodo -> info.getPatternValues().stream().map(patron -> metodo + " " + patron)))
                .filter(endpoint -> endpoint.contains(" /api/v1/"))
                .collect(Collectors.toSet());
        Set<String> clasificados = new HashSet<>(CON_SESION.keySet());
        clasificados.addAll(PUBLICOS);

        assertThat(clasificados).containsExactlyInAnyOrderElementsOf(declarados);
    }

    @TestFactory
    Stream<DynamicTest> unRolSinPermisoRecibe403YElIntentoQuedaEnLaAuditoria() {
        return CON_SESION.entrySet().stream()
                .flatMap(endpoint -> EnumSet.complementOf(EnumSet.copyOf(endpoint.getValue())).stream()
                        .map(rol -> DynamicTest.dynamicTest(rol + " en " + endpoint.getKey(), () -> {
                            UUID usuario = crearUsuario(rol);
                            String ruta = conIds(endpoint.getKey());

                            mvc.perform(solicitud(endpoint.getKey(), ruta).header("Authorization", sesionDe(usuario, rol)))
                                    .andExpect(status().isForbidden());

                            assertThat(denegacionesAuditadas(usuario, ruta)).isEqualTo(1);
                        })));
    }

    @TestFactory
    Stream<DynamicTest> sinSesionSoloSeUsanLosEndpointsPublicos() {
        return CON_SESION.keySet().stream()
                .map(endpoint -> DynamicTest.dynamicTest(endpoint, () ->
                        mvc.perform(solicitud(endpoint, conIds(endpoint))).andExpect(status().isUnauthorized())));
    }

    @Test
    void unAnfitrionNoPuedeEditarUnaPublicacionAjenaYElIntentoQuedaEnLaAuditoria() throws Exception {
        UUID publicacion = crearPublicacionActiva(crearUsuario(ANFITRION));
        UUID intruso = crearUsuario(ANFITRION);
        String ruta = "/api/v1/publicaciones/" + publicacion;

        mvc.perform(put(ruta).header("Authorization", sesionDe(intruso, ANFITRION))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Ahora es mía\"}"))
                .andExpect(status().isForbidden());

        assertThat(denegacionesAuditadas(intruso, ruta)).isEqualTo(1);
        assertThat(comoAplicacion.queryForObject("SELECT titulo FROM publicacion WHERE id = ?", String.class, publicacion))
                .isEqualTo("Casa de prueba");
    }

    /** "GET /api/v1/x/{id}" → GET con un id cualquiera. */
    private static MockHttpServletRequestBuilder solicitud(String endpoint, String ruta) {
        HttpMethod metodo = HttpMethod.valueOf(endpoint.substring(0, endpoint.indexOf(' ')));
        MockHttpServletRequestBuilder solicitud = request(metodo, ruta);
        return List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH).contains(metodo)
                ? solicitud.contentType(MediaType.APPLICATION_JSON).content("{}")
                : solicitud;
    }

    private static String conIds(String endpoint) {
        return endpoint.substring(endpoint.indexOf(' ') + 1).replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
    }

    private long denegacionesAuditadas(UUID usuario, String ruta) {
        return comoAplicacion.queryForObject("""
                SELECT count(*) FROM auditoria.evento_auditoria
                WHERE actor_id = ? AND accion = 'AccesoDenegado' AND detalle ->> 'ruta' = ?""", Long.class, usuario, ruta);
    }
}
