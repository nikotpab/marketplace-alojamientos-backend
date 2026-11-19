package com.inversioneslr.marketplace.reservas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.inversioneslr.marketplace.compartido.ReservaId;
import com.inversioneslr.marketplace.compartido.Tokens;
import com.inversioneslr.marketplace.cotizacion.FachadaCotizacion;
import com.inversioneslr.marketplace.pagos.FachadaPagos;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.reservas.dominio.PeriodoNoDisponibleException;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import com.inversioneslr.marketplace.usuarios.FachadaUsuarios;
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
 * SCRUM-127 (pendiente): módulo reservas en modo STANDALONE con los otros módulos simulados.
 * Requiere Docker porque los repositorios del módulo usan SQL de PostgreSQL.
 */
@ApplicationModuleTest(mode = BootstrapMode.STANDALONE, extraIncludes = {"compartido"})
@TestPropertySource(properties = {
        "marketplace.jwt-secreto=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "marketplace.pagos.proveedor=simulado",
        "marketplace.eventos.cron=-",
        "marketplace.pagos.reconciliacion-cron=-"})
class ReservasModuleIT {

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
    @MockitoBean FachadaCotizacion cotizacion;
    @MockitoBean FachadaPagos pagos;
    @MockitoBean FachadaPublicaciones publicaciones;
    @MockitoBean FachadaUsuarios usuarios;
    // Tokens está en el módulo usuarios (excluido en STANDALONE); necesario para ConfiguracionSeguridad.
    @MockitoBean Tokens tokens;

    @Autowired FachadaReservas fachadaReservas;
    @Autowired RepositorioReservas repositorioReservas;
    @Autowired JdbcTemplate jdbc;

    @Test
    void fachadaDeReservasDevuelveLosEstadosDeUnaReservaGuardada() {
        UUID huesped = crearUsuario();
        UUID publicacion = crearPublicacion(crearUsuario());
        LocalDate llegada = LocalDate.of(2030, 11, 10);

        UUID idReserva = jdbc.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado,
                                     noches, precio_base, tarifa_limpieza, tarifa_servicio, descuento, total)
                VALUES (?, ?, ?, ?, 2, 'CONFIRMADA', 3, 300000, 0, 30000, 0, 330000) RETURNING id""",
                UUID.class, huesped, publicacion, llegada, llegada.plusDays(3));

        var datos = fachadaReservas.datosReserva(new ReservaId(idReserva));

        assertThat(datos.huespedId()).isEqualTo(huesped);
        assertThat(datos.publicacionId()).isEqualTo(publicacion);
        assertThat(datos.estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(datos.total()).isEqualByComparingTo("330000");
        assertThat(datos.tarifaServicio()).isEqualByComparingTo("30000");
    }

    @Test
    void guardarPeriodoSolapadoLanzaPeriodoNoDisponible() {
        UUID huesped = crearUsuario();
        UUID publicacion = crearPublicacion(crearUsuario());
        LocalDate llegada = LocalDate.of(2030, 12, 10);

        // Primera reserva: se guarda sin problema
        when(publicaciones.datosPublicacion(any())).thenReturn(
                new DatosPublicacion(publicacion, UUID.randomUUID(), true, new BigDecimal("100000"), 4, "Flexible", List.of()));
        repositorioReservas.guardar(reserva(huesped, publicacion, llegada, llegada.plusDays(3)), List.of());

        // Segunda con periodo solapado: la restricción de BD rechaza el INSERT
        var solapada = reserva(huesped, publicacion, llegada.plusDays(1), llegada.plusDays(4));
        assertThatThrownBy(() -> repositorioReservas.guardar(solapada, List.of()))
                .isInstanceOf(PeriodoNoDisponibleException.class);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM reserva WHERE publicacion_id = ? AND estado = 'CONFIRMADA'",
                Long.class, publicacion)).isEqualTo(1L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID crearUsuario() {
        return jdbc.queryForObject("""
                INSERT INTO usuario (nombre, apellido, correo, telefono, hash_contrasena, rol)
                VALUES ('Test', 'User', ?, '3000000000', 'hash', 'HUESPED') RETURNING id""",
                UUID.class, UUID.randomUUID() + "@modulo-reservas.co");
    }

    private UUID crearPublicacion(UUID anfitrionId) {
        UUID pub = jdbc.queryForObject("""
                INSERT INTO publicacion (anfitrion_id, tipo_alojamiento_id, politica_cancelacion_id,
                        titulo, ciudad, capacidad, precio_noche, estado)
                VALUES (?, (SELECT id FROM tipo_alojamiento LIMIT 1),
                           (SELECT id FROM politica_cancelacion LIMIT 1),
                        'Casa modulo', 'Ciudad', 4, 100000, 'ACTIVA')
                RETURNING id""", UUID.class, anfitrionId);
        jdbc.update("""
                INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo)
                VALUES (?, '2030-01-01', '2031-01-01', 'DISPONIBLE')""", pub);
        return pub;
    }

    private static Reserva reserva(UUID huesped, UUID publicacion, LocalDate llegada, LocalDate salida) {
        BigDecimal valor = new BigDecimal("300000");
        return new Reserva(UUID.randomUUID(), null, huesped, publicacion, llegada, salida, 2,
                EstadoReserva.CONFIRMADA, 3, valor, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, valor);
    }
}
