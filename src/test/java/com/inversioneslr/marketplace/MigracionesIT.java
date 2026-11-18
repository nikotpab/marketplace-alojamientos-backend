package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.Arrays;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class MigracionesIT extends PruebaIntegracion {

    @Autowired
    Flyway flyway;

    @Test
    void seAplicaronTodasLasMigracionesHastaV11SinError() {
        var versionesAplicadas = Arrays.stream(flyway.info().applied())
                .filter(m -> m.getVersion() != null)
                .map(m -> m.getVersion().getVersion())
                .toList();

        for (int v = 1; v <= 11; v++) {
            assertThat(versionesAplicadas)
                    .as("V%d debe estar aplicada", v)
                    .contains(String.valueOf(v));
        }
        assertThat(flyway.info().applied())
                .as("ninguna migración debe haber fallado")
                .allMatch(m -> !m.getState().isFailed());
    }

    @Test
    void elConstraintAntiSolapeImpideReservasSuperpuestas() {
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        UUID huesped = crearUsuario(Rol.HUESPED);

        comoAplicacion.update("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, '2030-07-01', '2030-07-10', 2, 'CONFIRMADA')""", huesped, publicacion);

        assertThatThrownBy(() -> comoAplicacion.update("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, '2030-07-05', '2030-07-15', 2, 'CONFIRMADA')""", huesped, publicacion))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elTriggerSoloInsercionEstaRegistradoEnAuditoria() {
        long cantidad = comoAplicacion.queryForObject("""
                SELECT count(*) FROM pg_trigger t
                JOIN pg_class c ON c.oid = t.tgrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE t.tgname = 'solo_insercion'
                  AND c.relname = 'evento_auditoria'
                  AND n.nspname = 'auditoria'""", Long.class);

        assertThat(cantidad).isEqualTo(1);
    }

    @Test
    void laClavePrimariaDeSolicitudProcesadaImpideDuplicados() {
        UUID huesped = crearUsuario(Rol.HUESPED);
        String clave = "prueba-pk-" + UUID.randomUUID();

        comoAplicacion.update(
                "INSERT INTO solicitud_procesada (clave, huesped_id, resultado) VALUES (?, ?, '{}'::jsonb)",
                clave, huesped);

        assertThatThrownBy(() -> comoAplicacion.update(
                "INSERT INTO solicitud_procesada (clave, huesped_id, resultado) VALUES (?, ?, '{}'::jsonb)",
                clave, huesped))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
