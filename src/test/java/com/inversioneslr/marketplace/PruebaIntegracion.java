package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.Tokens;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Base de las pruebas de integración: Postgres real, usuario de aplicación restringido y tokens JWT reales. */
@SpringBootTest(properties = {
        // Solo para pruebas: valor de baja entropía con la longitud mínima que exige TokensJwt.
        "marketplace.jwt-secreto=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "marketplace.pagos.proveedor=simulado",
        "marketplace.eventos.cron=-",
        "marketplace.pagos.reconciliacion-cron=-"})
@AutoConfigureMockMvc
abstract class PruebaIntegracion {

    private static final String CLAVE = "solo-para-pruebas";

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("crear-roles.sql");

    /** SMTP en memoria: recibe los correos que envía la aplicación. */
    static final GreenMail correo = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    static {
        postgres.start();
        correo.start();
    }

    /**
     * Siempre GreenMail, sin autenticación ni TLS, aunque el .env local configure otro servidor (por ejemplo Gmail):
     * application.yml lo importa y sus valores no deben llegar a las pruebas.
     */
    @DynamicPropertySource
    static void servidorDeCorreo(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.mail.host", () -> correo.getSmtp().getBindTo());
        propiedades.add("spring.mail.port", () -> correo.getSmtp().getPort());
        propiedades.add("CORREO_SEGURO", () -> "false");
        propiedades.add("CORREO_USUARIO", () -> "");
        propiedades.add("CORREO_CONTRASENA", () -> "");
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry propiedades) {
        propiedades.add("DB_URL", () -> postgres.getJdbcUrl() + "&currentSchema=marketplace");
        propiedades.add("DB_USUARIO_APP", () -> "marketplace_app");
        propiedades.add("DB_CONTRASENA_APP", () -> CLAVE);
        propiedades.add("DB_USUARIO_MIGRACION", () -> "migrador");
        propiedades.add("DB_CONTRASENA_MIGRACION", () -> CLAVE);
    }

    @Autowired
    MockMvc mvc;

    /** Conectado como marketplace_app, igual que la aplicación. */
    @Autowired
    JdbcTemplate comoAplicacion;

    @Autowired
    Tokens tokens;

    /** El reloj de la aplicación, con la zona de Bogotá. */
    @Autowired
    Clock reloj;

    /** Hoy para la aplicación. LocalDate.now() usa la zona del sistema, que en el CI es UTC. */
    LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    UUID crearUsuario(Rol rol) {
        return comoAplicacion.queryForObject("""
                INSERT INTO usuario (nombre, apellido, correo, telefono, hash_contrasena, rol)
                VALUES ('Nombre', 'Apellido', ?, '3000000000', 'hash', ?) RETURNING id""",
                UUID.class, UUID.randomUUID() + "@prueba.co", rol.name());
    }

    /** Publicación ACTIVA con capacidad 4, precio 100.000 y disponible durante todo 2030. */
    UUID crearPublicacionActiva(UUID anfitrion) {
        UUID publicacion = comoAplicacion.queryForObject("""
                INSERT INTO publicacion (anfitrion_id, tipo_alojamiento_id, politica_cancelacion_id, titulo, ciudad, capacidad, precio_noche, estado)
                VALUES (?, (SELECT id FROM tipo_alojamiento LIMIT 1), (SELECT id FROM politica_cancelacion LIMIT 1),
                        'Casa de prueba', 'Cartagena', 4, 100000, 'ACTIVA')
                RETURNING id""", UUID.class, anfitrion);
        comoAplicacion.update("""
                INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo)
                VALUES (?, '2030-01-01', '2031-01-01', 'DISPONIBLE')""", publicacion);
        return publicacion;
    }

    /** Los correos que recibió esa dirección, del más antiguo al más reciente. */
    List<MimeMessage> correosPara(String direccion) {
        return Arrays.stream(correo.getReceivedMessages()).filter(mensaje -> esPara(mensaje, direccion)).toList();
    }

    /** El texto del último correo para esa dirección. Espera, porque los correos salen en segundo plano. */
    String ultimoCorreoPara(String direccion) throws MessagingException, IOException {
        await().atMost(Duration.ofSeconds(10)).until(() -> !correosPara(direccion).isEmpty());
        List<MimeMessage> recibidos = correosPara(direccion);
        return (String) recibidos.getLast().getContent();
    }

    /** El token del enlace a esa pantalla del frontend, por ejemplo "verificar-correo". */
    static String tokenDelEnlace(String texto, String pantalla) {
        Matcher enlace = Pattern.compile("/" + pantalla + "\\?token=([A-Za-z0-9_-]+)").matcher(texto);
        assertThat(enlace.find()).as("enlace a /%s en el correo", pantalla).isTrue();
        return enlace.group(1);
    }

    /** Pide el código de registro por la API y lo lee del correo, como haría la persona. */
    String codigoDeRegistro(String direccion) throws Exception {
        int antes = correosPara(direccion).size();
        mvc.perform(post("/api/v1/auth/register/code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\"}".formatted(direccion))).andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(10)).until(() -> correosPara(direccion).size() > antes);
        Matcher codigo = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)").matcher(ultimoCorreoPara(direccion));
        assertThat(codigo.find()).as("código de 6 dígitos en el correo").isTrue();
        return codigo.group(1);
    }

    private static boolean esPara(MimeMessage mensaje, String direccion) {
        try {
            Address[] destinatarios = mensaje.getAllRecipients();
            return destinatarios != null && Arrays.stream(destinatarios).anyMatch(d -> d.toString().equalsIgnoreCase(direccion));
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return valor de la cabecera Authorization con un JWT real para ese usuario */
    String sesionDe(UUID usuarioId, Rol rol) {
        return "Bearer " + tokens.emitir(new UsuarioAutenticado(usuarioId, rol));
    }
}
