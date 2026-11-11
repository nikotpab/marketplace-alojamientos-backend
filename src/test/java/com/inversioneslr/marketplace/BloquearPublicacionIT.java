package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** CU-24 · RF-27. */
class BloquearPublicacionIT extends PruebaIntegracion {

    UUID anfitrion;
    UUID publicacion;
    String administrador;

    @BeforeEach
    void preparar() {
        anfitrion = crearUsuario(Rol.ANFITRION);
        publicacion = crearPublicacionActiva(anfitrion);
        administrador = sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR);
    }

    @Test
    void laPublicacionQuedaBloqueadaConSuMotivoYSeAvisaAlAnfitrion() throws Exception {
        bloquear(administrador, "Fotos que no corresponden al alojamiento").andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/publicaciones/{id}", publicacion).header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"))
                .andExpect(jsonPath("$.motivoBloqueo").value("Fotos que no corresponden al alojamiento"))
                .andExpect(jsonPath("$.motivoRechazo").doesNotExist());
        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'PublicacionBloqueada'",
                publicacion)).isEqualTo(1);
        esperarEventosDe(publicacion);
        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ? AND tipo = 'PublicacionBloqueada'", anfitrion)).isEqualTo(1);
    }

    @Test
    void lasReservasFuturasPasanARevisionManualYSeAvisaAlHuesped() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        LocalDate hoy = hoy();
        UUID futura = reservar(huesped, hoy.plusDays(10), "CONFIRMADA");
        UUID enCurso = reservar(huesped, hoy.minusDays(1), "CONFIRMADA");
        UUID cancelada = reservar(huesped, hoy.plusDays(10), "CANCELADA");

        bloquear(administrador, "Información engañosa").andExpect(status().isNoContent());

        assertThat(estado(futura)).isEqualTo("EN_REVISION");
        assertThat(estado(enCurso)).isEqualTo("CONFIRMADA");
        assertThat(estado(cancelada)).isEqualTo("CANCELADA");
        esperarEventosDe(futura);
        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ? AND tipo = 'ReservaEnRevision'", huesped)).isEqualTo(1);
    }

    @Test
    void elAnfitrionNoPuedeReactivarUnaPublicacionBloqueada() throws Exception {
        bloquear(administrador, "Información engañosa").andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/publicaciones/{id}/reactivar", publicacion).header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isConflict());
    }

    @Test
    void unaPublicacionYaBloqueadaNoSeVuelveABloquear() throws Exception {
        bloquear(administrador, "Información engañosa").andExpect(status().isNoContent());

        bloquear(administrador, "Otra vez").andExpect(status().isConflict());
    }

    @Test
    void elMotivoEsObligatorio() throws Exception {
        bloquear(administrador, " ").andExpect(status().isBadRequest());

        assertThat(comoAplicacion.queryForObject("SELECT estado FROM publicacion WHERE id = ?", String.class, publicacion)).isEqualTo("ACTIVA");
    }

    @Test
    void soloElAdministradorBloquea() throws Exception {
        bloquear(sesionDe(anfitrion, Rol.ANFITRION), "Me bloqueo yo").andExpect(status().isForbidden());
    }

    private ResultActions bloquear(String sesion, String motivo) throws Exception {
        return mvc.perform(post("/api/v1/admin/publicaciones/{id}/bloqueo", publicacion)
                .header("Authorization", sesion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + motivo + "\"}"));
    }

    private UUID reservar(UUID huesped, LocalDate llegada, String estado) {
        return comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, ?, ?, 1, ?) RETURNING id""", UUID.class, huesped, publicacion, llegada, llegada.plusDays(2), estado);
    }

    private String estado(UUID reserva) {
        return comoAplicacion.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, reserva);
    }

    private long contar(String sql, UUID id) {
        return comoAplicacion.queryForObject(sql, Long.class, id);
    }
}
