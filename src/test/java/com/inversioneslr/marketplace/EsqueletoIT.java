package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Verifica el arranque, la denegación por defecto y las garantías de la base de datos. */
class EsqueletoIT extends PruebaIntegracion {

    @Test
    void aplicacionArranca() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void rutaSinDeclararSeDeniega() throws Exception {
        String huesped = sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED);
        mvc.perform(get("/api/v1/no-declarada")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/no-declarada").header("Authorization", huesped)).andExpect(status().isForbidden());
    }

    @Test
    void rutaDeAdministradorExigeElRol() throws Exception {
        String huesped = sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED);
        String administrador = sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR);
        mvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", huesped)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", administrador)).andExpect(status().isNotImplemented());
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

    private UUID crearPublicacion() {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
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
