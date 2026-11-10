package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** SCRUM-68 · RNF-19, RNF-22, RNF-23: inyección SQL, XSS, fuerza bruta y contraseñas. */
@ExtendWith(OutputCaptureExtension.class)
class SeguridadIT extends PruebaIntegracion {

    private static final String CONTRASENA = "Contrasena-de-prueba-1";

    @Test
    void unaInyeccionSqlEnLaBusquedaNoDevuelveNadaNiBorraDatos() throws Exception {
        long usuarios = contarUsuarios();

        for (String ataque : new String[] {"' OR '1'='1", "x'; DROP TABLE usuario; --", "Cartagena' UNION SELECT correo FROM usuario --"}) {
            mvc.perform(get("/api/v1/alojamientos").param("ciudad", ataque))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));
        }

        assertThat(contarUsuarios()).isGreaterThanOrEqualTo(usuarios);
    }

    @Test
    void unaInyeccionSqlEnElInicioDeSesionNoAbreNingunaCuenta() throws Exception {
        registrar();

        iniciarSesion("' OR '1'='1' --", "' OR '1'='1").andExpect(status().isUnauthorized());
        iniciarSesion("admin@prueba.co' --", "cualquiera").andExpect(status().isUnauthorized());
    }

    @Test
    void unaResenaConHtmlSeDevuelveComoTextoEnJsonSinInterpretarse() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        UUID reserva = comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, '2029-01-01', '2029-01-03', 1, 'COMPLETADA') RETURNING id""", UUID.class, huesped, publicacion);
        String ataque = "<script>alert('xss')</script><img src=x onerror=alert(1)>";

        mvc.perform(post("/api/v1/reservas/{id}/resena", reserva).header("Authorization", sesionDe(huesped, Rol.HUESPED))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"calificacion\":5,\"contenido\":\"" + ataque.replace("'", "\\u0027") + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.comentario").value(ataque));
    }

    @Test
    void cincoIntentosFallidosBloqueanLaCuentaTreintaMinutosYTodosLosIntentosQuedanRegistrados() throws Exception {
        String correo = registrar();
        UUID usuario = comoAplicacion.queryForObject("SELECT id FROM usuario WHERE correo = ?", UUID.class, correo);

        for (int intento = 1; intento <= 4; intento++) {
            iniciarSesion(correo, "otra-contrasena").andExpect(status().isUnauthorized());
        }
        iniciarSesion(correo, "otra-contrasena").andExpect(status().isLocked());
        iniciarSesion(correo, CONTRASENA).andExpect(status().isLocked());

        Instant hasta = comoAplicacion.queryForObject("SELECT bloqueado_hasta FROM usuario WHERE id = ?", Timestamp.class, usuario)
                .toInstant();
        assertThat(Duration.between(Instant.now(), hasta)).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));
        assertThat(comoAplicacion.queryForList("""
                SELECT resultado FROM auditoria.evento_auditoria WHERE actor_id = ? AND accion = 'InicioSesionFallido'
                ORDER BY creado_en""", String.class, usuario))
                .containsExactly("FALLIDO", "FALLIDO", "FALLIDO", "FALLIDO", "FALLIDO", "CUENTA_BLOQUEADA");
    }

    @Test
    void laContrasenaSeGuardaConHashYSaltYNoApareceEnLosLogs(CapturedOutput salida) throws Exception {
        String primero = registrar();
        String segundo = registrar();

        String hash = hashDe(primero);
        assertThat(hash).startsWith("$2").doesNotContain(CONTRASENA);
        assertThat(hashDe(segundo)).as("la misma contraseña con otro salt da otro hash").isNotEqualTo(hash);
        assertThat(salida.getOut()).doesNotContain(CONTRASENA);
    }

    private String registrar() throws Exception {
        String correo = UUID.randomUUID() + "@prueba.co";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Laura","apellido":"Gómez","correo":"%s","telefono":"3001234567","contrasena":"%s","rol":"HUESPED"}"""
                .formatted(correo, CONTRASENA))).andExpect(status().isCreated());
        return correo;
    }

    private ResultActions iniciarSesion(String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)));
    }

    private String hashDe(String correo) {
        return comoAplicacion.queryForObject("SELECT hash_contrasena FROM usuario WHERE correo = ?", String.class, correo);
    }

    private long contarUsuarios() {
        return comoAplicacion.queryForObject("SELECT count(*) FROM usuario", Long.class);
    }
}
