package com.inversioneslr.marketplace;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.reservas.aplicacion.ServicioReservas;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Tres recorridos de sistema contra la API real con HTTP auténtico (RestAssured).
 * Reutiliza Testcontainers/PostgreSQL y GreenMail de PruebaIntegracion.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "marketplace.jwt-secreto=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "marketplace.pagos.proveedor=simulado",
        "marketplace.eventos.cron=-",
        "marketplace.pagos.reconciliacion-cron=-"})
class RecorridosSistemaIT extends PruebaIntegracion {

    @LocalServerPort
    int puerto;

    @Autowired
    ServicioReservas servicioReservas;

    @BeforeEach
    void configurarRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = puerto;
        RestAssured.defaultParser = Parser.JSON;
        RestAssured.config = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig().dontReuseHttpClientInstance());
    }

    /**
     * a. Recorrido del huésped: registro, verificación de correo, sesión, búsqueda, cotización,
     * reserva con proveedor simulado, cancelación con devolución, completar y reseñar, reporte.
     */
    @Test
    void recorridoHuesped() throws Exception {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID publicacion = crearPublicacionActiva(anfitrion);
        String ciudad = "RecorridoHuesped-" + UUID.randomUUID().toString().substring(0, 8);
        comoAplicacion.update("UPDATE publicacion SET ciudad = ? WHERE id = ?", ciudad, publicacion);

        // 1. Registro: solicitar código, leer desde GreenMail y registrar
        String correoHuesped = UUID.randomUUID() + "@recorrido.co";
        int antesRegistro = correosPara(correoHuesped).size();
        given().contentType(ContentType.JSON)
                .body("{\"correo\":\"%s\"}".formatted(correoHuesped))
                .post("/api/v1/auth/register/code")
                .then().statusCode(202);
        await().atMost(Duration.ofSeconds(10)).until(() -> correosPara(correoHuesped).size() > antesRegistro);
        String codigoRegistro = extraerCodigo(ultimoCorreoPara(correoHuesped));

        String idHuesped = given().contentType(ContentType.JSON)
                .body("""
                        {"nombre":"Carmen","apellido":"Ruiz","correo":"%s","telefono":"3012345678",\
"contrasena":"Contrasena-Sistema-1","rol":"HUESPED","codigo":"%s"}"""
                        .formatted(correoHuesped, codigoRegistro))
                .post("/api/v1/auth/register")
                .then().statusCode(201)
                .extract().path("id");

        // 2. Inicio de sesión
        String tokenHuesped = given().contentType(ContentType.JSON)
                .body("{\"correo\":\"%s\",\"contrasena\":\"Contrasena-Sistema-1\"}".formatted(correoHuesped))
                .post("/api/v1/auth/login")
                .then().statusCode(200)
                .extract().path("token");

        // 3. Búsqueda
        int encontrados = given().header("Authorization", "Bearer " + tokenHuesped)
                .get("/api/v1/alojamientos?ciudad=" + ciudad)
                .then().statusCode(200)
                .extract().path("total");
        assertThat(encontrados).isGreaterThanOrEqualTo(1);

        // 4. Cotización
        Number cotizacionTotal = given().header("Authorization", "Bearer " + tokenHuesped)
                .contentType(ContentType.JSON)
                .body("""
                        {"publicacionId":"%s","llegada":"2030-06-10","salida":"2030-06-13","huespedes":2,"adicionales":[]}"""
                        .formatted(publicacion))
                .post("/api/v1/cotizaciones")
                .then().statusCode(200)
                .extract().path("total");
        assertThat(cotizacionTotal.doubleValue()).isGreaterThan(0);

        // 5. Reservar con proveedor simulado
        String claveIdempotencia = UUID.randomUUID().toString();
        String idReserva = given().header("Authorization", "Bearer " + tokenHuesped)
                .header("Idempotency-Key", claveIdempotencia)
                .contentType(ContentType.JSON)
                .body("""
                        {"publicacionId":"%s","llegada":"2030-06-10","salida":"2030-06-13","huespedes":2,"adicionales":[]}"""
                        .formatted(publicacion))
                .post("/api/v1/reservas")
                .then().statusCode(201)
                .extract().path("id");

        // Sin reservas duplicadas para el mismo periodo
        assertThat(comoAplicacion.queryForObject(
                "SELECT count(*) FROM reserva WHERE publicacion_id = ? AND estado = 'CONFIRMADA' AND fecha_llegada = '2030-06-10'",
                Long.class, publicacion)).isEqualTo(1L);
        // Sin nulos en campos obligatorios
        var datos = comoAplicacion.queryForMap(
                "SELECT huesped_id, publicacion_id, fecha_llegada, fecha_salida, total FROM reserva WHERE id = ?::uuid", idReserva);
        assertThat(datos.get("huesped_id")).isNotNull();
        assertThat(datos.get("publicacion_id")).isNotNull();
        assertThat(datos.get("total")).isNotNull();

        // 6. Cancelar con devolución
        given().header("Authorization", "Bearer " + tokenHuesped)
                .contentType(ContentType.JSON)
                .body("{\"motivo\":\"Cambié de planes\"}")
                .post("/api/v1/reservas/{id}/cancelacion", idReserva)
                .then().statusCode(200);
        assertThat(comoAplicacion.queryForObject(
                "SELECT estado FROM reserva WHERE id = ?::uuid", String.class, idReserva)).isEqualTo("CANCELADA");
        assertThat(comoAplicacion.queryForObject(
                "SELECT estado FROM pago WHERE reserva_id = ?::uuid", String.class, idReserva)).isEqualTo("DEVUELTO");

        // 7. Nueva reserva en fechas pasadas → completar → reseñar
        UUID huespedUuid = UUID.fromString(idHuesped);
        UUID reservaPasada = comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, '2024-01-05', '2024-01-08', 2, 'CONFIRMADA') RETURNING id""",
                UUID.class, huespedUuid, publicacion);
        servicioReservas.completarReservasFinalizadas();
        assertThat(comoAplicacion.queryForObject(
                "SELECT estado FROM reserva WHERE id = ?", String.class, reservaPasada)).isEqualTo("COMPLETADA");

        given().header("Authorization", "Bearer " + tokenHuesped)
                .contentType(ContentType.JSON)
                .body("{\"calificacion\":5,\"contenido\":\"Excelente experiencia en el recorrido\"}")
                .post("/api/v1/reservas/{id}/resena", reservaPasada)
                .then().statusCode(201);

        // 8. Administrador exporta reporte
        UUID adminId = crearUsuario(Rol.ADMINISTRADOR);
        String tokenAdmin = sesionDe(adminId, Rol.ADMINISTRADOR);

        byte[] pdf = given().header("Authorization", tokenAdmin)
                .get("/api/v1/admin/reportes?tipo=RESERVAS&formato=PDF")
                .then().statusCode(200)
                .extract().asByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // 9. Dashboard: totales coherentes con la BD (mismo período de 30 días que usa el endpoint por defecto)
        int completadasDashboard = given().header("Authorization", tokenAdmin)
                .get("/api/v1/admin/dashboard")
                .then().statusCode(200)
                .extract().path("reservasPorEstado.COMPLETADA");
        long completadasDb = comoAplicacion.queryForObject("""
                SELECT count(*) FROM reserva WHERE estado = 'COMPLETADA'
                AND (creado_en AT TIME ZONE 'America/Bogota')::date >= current_date - 29""",
                Long.class);
        assertThat((long) completadasDashboard).isEqualTo(completadasDb);
    }

    /** b. Recorrido del anfitrión: publicar y que el administrador apruebe. */
    @Test
    void recorridoAnfitrion() throws Exception {
        // 1. Registrar anfitrión via API real
        String correoAnfitrion = UUID.randomUUID() + "@anfitrion.co";
        int antesRegistro = correosPara(correoAnfitrion).size();
        given().contentType(ContentType.JSON)
                .body("{\"correo\":\"%s\"}".formatted(correoAnfitrion))
                .post("/api/v1/auth/register/code")
                .then().statusCode(202);
        await().atMost(Duration.ofSeconds(10)).until(() -> correosPara(correoAnfitrion).size() > antesRegistro);
        String codigoAnfitrion = extraerCodigo(ultimoCorreoPara(correoAnfitrion));

        given().contentType(ContentType.JSON)
                .body("""
                        {"nombre":"Pedro","apellido":"López","correo":"%s","telefono":"3109876543",\
"contrasena":"Contrasena-Sistema-1","rol":"ANFITRION","codigo":"%s"}"""
                        .formatted(correoAnfitrion, codigoAnfitrion))
                .post("/api/v1/auth/register")
                .then().statusCode(201);

        // 2. Login
        String tokenAnfitrion = given().contentType(ContentType.JSON)
                .body("{\"correo\":\"%s\",\"contrasena\":\"Contrasena-Sistema-1\"}".formatted(correoAnfitrion))
                .post("/api/v1/auth/login")
                .then().statusCode(200)
                .extract().path("token");

        // 3. Crear borrador
        String idPublicacion = given().header("Authorization", "Bearer " + tokenAnfitrion)
                .contentType(ContentType.JSON)
                .body(CrearPublicacionIT.COMPLETA)
                .post("/api/v1/publicaciones")
                .then().statusCode(201)
                .extract().path("id");
        assertThat(comoAplicacion.queryForObject(
                "SELECT estado FROM publicacion WHERE id = ?::uuid", String.class, UUID.fromString(idPublicacion)))
                .isEqualTo("BORRADOR");

        // 4. Solicitar activación → PENDIENTE_REVISION
        given().header("Authorization", "Bearer " + tokenAnfitrion)
                .post("/api/v1/publicaciones/{id}/solicitar-activacion", idPublicacion)
                .then().statusCode(200);
        assertThat(comoAplicacion.queryForObject(
                "SELECT estado FROM publicacion WHERE id = ?::uuid", String.class, UUID.fromString(idPublicacion)))
                .isEqualTo("PENDIENTE_REVISION");

        // 5. Administrador aprueba → ACTIVA
        UUID adminId = crearUsuario(Rol.ADMINISTRADOR);
        String tokenAdmin = sesionDe(adminId, Rol.ADMINISTRADOR);
        given().header("Authorization", tokenAdmin)
                .post("/api/v1/admin/publicaciones/{id}/aprobar", idPublicacion)
                .then().statusCode(200);

        assertThat(comoAplicacion.queryForObject(
                "SELECT estado FROM publicacion WHERE id = ?::uuid", String.class, UUID.fromString(idPublicacion)))
                .isEqualTo("ACTIVA");
        assertThat(comoAplicacion.queryForObject("""
                SELECT count(*) FROM auditoria.evento_auditoria
                WHERE entidad_id = ?::uuid AND accion = 'PublicacionAprobada'""",
                Long.class, UUID.fromString(idPublicacion))).isEqualTo(1L);
    }

    /** c. Recorrido de moderación: bloquear publicación y restringir usuario. */
    @Test
    void recorridoModeracion() {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID publicacion = crearPublicacionActiva(anfitrion);
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID reserva = comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, '2030-09-01', '2030-09-04', 2, 'CONFIRMADA') RETURNING id""",
                UUID.class, huesped, publicacion);

        UUID adminId = crearUsuario(Rol.ADMINISTRADOR);
        String tokenAdmin = sesionDe(adminId, Rol.ADMINISTRADOR);

        // 1. Bloquear publicación → reserva futura pasa a EN_REVISION
        given().header("Authorization", tokenAdmin)
                .contentType(ContentType.JSON)
                .body("{\"motivo\":\"Contenido engañoso\"}")
                .post("/api/v1/admin/publicaciones/{id}/bloqueo", publicacion)
                .then().statusCode(204);

        assertThat(comoAplicacion.queryForObject(
                "SELECT estado FROM publicacion WHERE id = ?", String.class, publicacion)).isEqualTo("BLOQUEADA");
        assertThat(comoAplicacion.queryForObject(
                "SELECT estado FROM reserva WHERE id = ?", String.class, reserva)).isEqualTo("EN_REVISION");
        assertThat(comoAplicacion.queryForObject(
                "SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'PublicacionBloqueada'",
                Long.class, publicacion)).isEqualTo(1L);

        // 2. Restringir huésped
        given().header("Authorization", tokenAdmin)
                .contentType(ContentType.JSON)
                .body("{\"motivo\":\"Comportamiento inapropiado\"}")
                .post("/api/v1/admin/usuarios/{id}/restriccion", huesped)
                .then().statusCode(204);

        assertThat(comoAplicacion.queryForObject(
                "SELECT restringido FROM usuario WHERE id = ?", Boolean.class, huesped)).isTrue();
        assertThat(comoAplicacion.queryForObject(
                "SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'UsuarioRestringido'",
                Long.class, huesped)).isEqualTo(1L);
    }

    private static String extraerCodigo(String textoCorreo) {
        Matcher m = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)").matcher(textoCorreo);
        assertThat(m.find()).as("código de 6 dígitos en el correo").isTrue();
        return m.group(1);
    }
}
