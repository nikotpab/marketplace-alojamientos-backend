package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.inversioneslr.marketplace.compartido.dominio.Tokens;
import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifica lo que ya es real en el esqueleto: arranque, denegación por defecto y garantías de la base de datos. */
@SpringBootTest(properties = {"marketplace.jwt-secreto=solo-para-pruebas", "marketplace.pagos.proveedor=simulado"})
@AutoConfigureMockMvc
@Testcontainers
class EsqueletoIT {

    private static final String CLAVE = "solo-para-pruebas";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("crear-roles.sql");

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

    @MockitoBean
    Tokens tokens;

    @Test
    void aplicacionArranca() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void casoDeUsoPendienteResponde501() throws Exception {
        mvc.perform(get("/api/v1/alojamientos")).andExpect(status().isNotImplemented());
    }

    @Test
    void rutaSinDeclararSeDeniega() throws Exception {
        sesion("huesped", Rol.HUESPED);
        mvc.perform(get("/api/v1/no-declarada")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/no-declarada").header("Authorization", "Bearer huesped")).andExpect(status().isForbidden());
    }

    @Test
    void rutaDeAdministradorExigeElRol() throws Exception {
        sesion("huesped", Rol.HUESPED);
        sesion("admin", Rol.ADMINISTRADOR);
        mvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer huesped")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer admin")).andExpect(status().isNotImplemented());
    }

    @Test
    void reservasConfirmadasNoSeSolapan() {
        UUID publicacion = crearPublicacion();
        LocalDate dia = LocalDate.of(2030, 1, 10);

        insertarReserva(publicacion, dia, dia.plusDays(3), "CONFIRMADA");

        assertThatThrownBy(() -> insertarReserva(publicacion, dia.plusDays(2), dia.plusDays(4), "CONFIRMADA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("reserva_sin_solape");
        // Una cancelada no ocupa el periodo, y otra puede empezar el día en que la anterior termina.
        insertarReserva(publicacion, dia, dia.plusDays(3), "CANCELADA");
        insertarReserva(publicacion, dia.plusDays(3), dia.plusDays(5), "CONFIRMADA");
    }

    @Test
    void auditoriaEsSoloInsercionParaCualquierRol() {
        String insertar = "INSERT INTO auditoria.evento_auditoria (accion, entidad, resultado, hash_registro) VALUES ('a', 'e', 'r', 'h')";
        String modificar = "UPDATE auditoria.evento_auditoria SET accion = 'x'";
        String borrar = "DELETE FROM auditoria.evento_auditoria";
        JdbcTemplate comoSuperusuario = new JdbcTemplate(new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true));

        assertThat(comoAplicacion.update(insertar)).isEqualTo(1);

        for (JdbcTemplate rol : new JdbcTemplate[] {comoAplicacion, comoSuperusuario}) {
            assertThatThrownBy(() -> rol.update(modificar)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> rol.update(borrar)).isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> comoSuperusuario.execute("TRUNCATE auditoria.evento_auditoria")).isInstanceOf(DataAccessException.class);
    }

    private void sesion(String token, Rol rol) {
        when(tokens.verificar(token)).thenReturn(Optional.of(new UsuarioAutenticado(UUID.randomUUID(), rol)));
    }

    private UUID crearPublicacion() {
        UUID anfitrion = comoAplicacion.queryForObject(
                "INSERT INTO usuario (correo, hash_contrasena, rol) VALUES (?, 'hash', 'ANFITRION') RETURNING id",
                UUID.class, UUID.randomUUID() + "@prueba.co");
        return comoAplicacion.queryForObject("""
                INSERT INTO publicacion (anfitrion_id, tipo_alojamiento_id, politica_cancelacion_id)
                VALUES (?, (SELECT id FROM tipo_alojamiento LIMIT 1), (SELECT id FROM politica_cancelacion LIMIT 1))
                RETURNING id""", UUID.class, anfitrion);
    }

    private void insertarReserva(UUID publicacion, LocalDate llegada, LocalDate salida, String estado) {
        comoAplicacion.update("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES ((SELECT anfitrion_id FROM publicacion WHERE id = ?), ?, ?, ?, 1, ?)""",
                publicacion, publicacion, llegada, salida, estado);
    }
}
