package com.inversioneslr.marketplace.cancelaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.inversioneslr.marketplace.cancelaciones.aplicacion.ServicioCancelaciones;
import com.inversioneslr.marketplace.cancelaciones.aplicacion.ServicioCancelaciones.CancelarReservaComando;
import com.inversioneslr.marketplace.cancelaciones.dominio.Cancelacion.EstadoFinal;
import com.inversioneslr.marketplace.compartido.ReservaId;
import com.inversioneslr.marketplace.compartido.Tokens;
import com.inversioneslr.marketplace.pagos.FachadaPagos;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.reservas.EstadoReserva;
import com.inversioneslr.marketplace.reservas.FachadaReservas;
import com.inversioneslr.marketplace.reservas.FachadaReservas.DatosReserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * SCRUM-127 (pendiente): módulo cancelaciones en modo STANDALONE con los otros módulos simulados.
 * Requiere Docker porque el repositorio del módulo usa SQL de PostgreSQL.
 */
@ApplicationModuleTest(mode = BootstrapMode.STANDALONE, extraIncludes = {"compartido"})
@TestPropertySource(properties = {
        "marketplace.jwt-secreto=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "marketplace.pagos.proveedor=simulado",
        "marketplace.eventos.cron=-",
        "marketplace.pagos.reconciliacion-cron=-"})
class CancelacionesModuleIT {

    private static final String CLAVE_BD = "solo-para-pruebas";

    @SuppressWarnings({"resource", "rawtypes"})
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine").withInitScript("crear-roles.sql");

    static final GreenMail correo = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    static {
        postgres.start();
        correo.start();
    }

    @DynamicPropertySource
    static void infraestructura(DynamicPropertyRegistry r) {
        r.add("DB_URL", () -> postgres.getJdbcUrl() + "&currentSchema=marketplace");
        r.add("DB_USUARIO_APP", () -> "marketplace_app");
        r.add("DB_CONTRASENA_APP", () -> CLAVE_BD);
        r.add("DB_USUARIO_MIGRACION", () -> "migrador");
        r.add("DB_CONTRASENA_MIGRACION", () -> CLAVE_BD);
        r.add("spring.mail.host", () -> correo.getSmtp().getBindTo());
        r.add("spring.mail.port", () -> correo.getSmtp().getPort());
        r.add("CORREO_SEGURO", () -> "false");
        r.add("CORREO_USUARIO", () -> "");
        r.add("CORREO_CONTRASENA", () -> "");
    }

    // Fachadas de otros módulos: Spring Modulith las excluye en STANDALONE; @MockitoBean las provee.
    @MockitoBean FachadaReservas reservas;
    @MockitoBean FachadaPublicaciones publicaciones;
    @MockitoBean FachadaPagos pagos;
    // Tokens está en el módulo usuarios (excluido en STANDALONE); necesario para ConfiguracionSeguridad.
    @MockitoBean Tokens tokens;

    @Autowired ServicioCancelaciones servicioCancelaciones;
    @Autowired JdbcTemplate jdbc;

    @Test
    void estimarDevolucionConPoliticaFlexibleNoGuardaNada() {
        UUID reservaId = UUID.randomUUID();
        UUID huespedId = UUID.randomUUID();
        LocalDate llegada = LocalDate.of(2030, 5, 20);

        when(reservas.datosReserva(new ReservaId(reservaId))).thenReturn(new DatosReserva(
                reservaId, huespedId, UUID.randomUUID(), llegada,
                EstadoReserva.CONFIRMADA, new BigDecimal("330000"), new BigDecimal("30000")));
        when(publicaciones.datosPublicacion(any())).thenReturn(
                new DatosPublicacion(UUID.randomUUID(), UUID.randomUUID(), true,
                        new BigDecimal("100000"), 4, "Flexible", List.of()));

        var estimacion = servicioCancelaciones.estimarDevolucion(huespedId, reservaId);

        // Flexible con 5+ días: devuelve total menos tarifa de servicio
        assertThat(estimacion.valorDevolucion()).isEqualByComparingTo("300000");
        assertThat(estimacion.politica()).isEqualTo("Flexible");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cancelacion", Long.class)).isZero();
    }

    @Test
    void cancelarReservaConDevolucionGuardaLaCancelacionEnBD() {
        UUID huespedId = crearUsuario();
        UUID publicacionId = crearPublicacion(crearUsuario());
        LocalDate llegada = LocalDate.of(2030, 6, 20);
        UUID reservaId = jdbc.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado,
                                     noches, precio_base, tarifa_limpieza, tarifa_servicio, descuento, total)
                VALUES (?, ?, ?, ?, 2, 'CONFIRMADA', 3, 300000, 0, 30000, 0, 330000) RETURNING id""",
                UUID.class, huespedId, publicacionId, llegada, llegada.plusDays(3));

        when(reservas.datosReserva(new ReservaId(reservaId))).thenReturn(new DatosReserva(
                reservaId, huespedId, publicacionId, llegada,
                EstadoReserva.CONFIRMADA, new BigDecimal("330000"), new BigDecimal("30000")));
        when(publicaciones.datosPublicacion(any())).thenReturn(
                new DatosPublicacion(publicacionId, UUID.randomUUID(), true,
                        new BigDecimal("100000"), 4, "Flexible", List.of()));
        // pagos.devolverPagoDeReserva es void: el mock no hace nada (éxito simulado)

        var cancelacion = servicioCancelaciones.cancelarReserva(
                huespedId, reservaId, new CancelarReservaComando("Cambio de planes"));

        assertThat(cancelacion.estadoFinal()).isEqualTo(EstadoFinal.DEVOLUCION_PROCESADA);
        assertThat(cancelacion.valorDevolucion()).isEqualByComparingTo("300000");
        assertThat(jdbc.queryForObject(
                "SELECT estado_final FROM cancelacion WHERE reserva_id = ?", String.class, reservaId))
                .isEqualTo("DEVOLUCION_PROCESADA");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID crearUsuario() {
        return jdbc.queryForObject("""
                INSERT INTO usuario (nombre, apellido, correo, telefono, hash_contrasena, rol)
                VALUES ('Test', 'User', ?, '3000000000', 'hash', 'HUESPED') RETURNING id""",
                UUID.class, UUID.randomUUID() + "@modulo-cancelaciones.co");
    }

    private UUID crearPublicacion(UUID anfitrionId) {
        UUID pub = jdbc.queryForObject("""
                INSERT INTO publicacion (anfitrion_id, tipo_alojamiento_id, politica_cancelacion_id,
                        titulo, ciudad, capacidad, precio_noche, estado)
                VALUES (?, (SELECT id FROM tipo_alojamiento LIMIT 1),
                           (SELECT id FROM politica_cancelacion LIMIT 1),
                        'Casa modulo-cancelaciones', 'Ciudad', 4, 100000, 'ACTIVA')
                RETURNING id""", UUID.class, anfitrionId);
        jdbc.update("""
                INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo)
                VALUES (?, '2030-01-01', '2031-01-01', 'DISPONIBLE')""", pub);
        return pub;
    }
}
