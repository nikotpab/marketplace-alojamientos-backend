package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** SCRUM-112 · CU-05 · pendientes 10 y 11 del README: el administrador revierte un bloqueo o una restricción. */
class RevertirModeracionIT extends PruebaIntegracion {

    UUID anfitrion;
    UUID publicacion;
    UUID administrador;
    String sesionAdministrador;

    @BeforeEach
    void preparar() {
        anfitrion = crearUsuario(Rol.ANFITRION);
        publicacion = crearPublicacionActiva(anfitrion);
        administrador = crearUsuario(Rol.ADMINISTRADOR);
        sesionAdministrador = sesionDe(administrador, Rol.ADMINISTRADOR);
    }

    @Test
    void desbloquearDevuelveLaPublicacionAActivaYSusReservasEnRevisionAConfirmada() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID futura = reservar(huesped, hoy().plusDays(10), "CONFIRMADA");
        UUID cancelada = reservar(huesped, hoy().plusDays(20), "CANCELADA");
        mvc.perform(post("/api/v1/admin/publicaciones/{id}/bloqueo", publicacion).header("Authorization", sesionAdministrador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Información engañosa\"}"))
                .andExpect(status().isNoContent());
        assertThat(texto("SELECT estado FROM reserva WHERE id = ?", futura)).isEqualTo("EN_REVISION");

        desbloquear().andExpect(status().isNoContent());

        assertThat(texto("SELECT estado FROM publicacion WHERE id = ?", publicacion)).isEqualTo("ACTIVA");
        assertThat(texto("SELECT motivo_estado FROM publicacion WHERE id = ?", publicacion)).isNull();
        assertThat(texto("SELECT estado FROM reserva WHERE id = ?", futura)).isEqualTo("CONFIRMADA");
        assertThat(texto("SELECT estado FROM reserva WHERE id = ?", cancelada)).isEqualTo("CANCELADA");
        assertThat(auditadas("PublicacionDesbloqueada", publicacion)).isEqualTo(1);
        assertThat(auditadas("ReservaRestablecida", futura)).isEqualTo(1);
        assertThat(notificaciones(anfitrion, "PublicacionDesbloqueada")).isEqualTo(1);
        assertThat(notificaciones(huesped, "ReservaRestablecida")).isEqualTo(1);
    }

    @Test
    void soloUnaPublicacionBloqueadaSeDesbloquea() throws Exception {
        desbloquear().andExpect(status().isConflict());
    }

    @Test
    void levantarLaRestriccionLaQuitaConSuMotivoYAvisaAlUsuario() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        mvc.perform(post("/api/v1/admin/usuarios/{id}/restriccion", huesped).header("Authorization", sesionAdministrador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Fraude\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/v1/admin/usuarios/{id}/restriccion", huesped).header("Authorization", sesionAdministrador))
                .andExpect(status().isNoContent());

        assertThat(comoAplicacion.queryForObject("SELECT restringido FROM usuario WHERE id = ?", Boolean.class, huesped)).isFalse();
        assertThat(texto("SELECT motivo_restriccion FROM usuario WHERE id = ?", huesped)).isNull();
        assertThat(comoAplicacion.queryForObject("""
                SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'RestriccionLevantada' AND actor_id = ?""",
                Long.class, huesped, administrador)).isEqualTo(1);
        assertThat(notificaciones(huesped, "RestriccionLevantada")).isEqualTo(1);
    }

    @Test
    void levantarLaRestriccionDeQuienNoLaTieneRespondeConflicto() throws Exception {
        mvc.perform(delete("/api/v1/admin/usuarios/{id}/restriccion", crearUsuario(Rol.HUESPED)).header("Authorization", sesionAdministrador))
                .andExpect(status().isConflict());
    }

    @Test
    void elAdministradorReactivaUnaCuentaDesactivadaYSeAvisaAlUsuario() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        comoAplicacion.update("UPDATE usuario SET cuenta_activa = false WHERE id = ?", huesped);

        mvc.perform(post("/api/v1/admin/usuarios/{id}/reactivacion", huesped).header("Authorization", sesionAdministrador))
                .andExpect(status().isNoContent());

        assertThat(comoAplicacion.queryForObject("SELECT cuenta_activa FROM usuario WHERE id = ?", Boolean.class, huesped)).isTrue();
        assertThat(auditadas("CuentaReactivada", huesped)).isEqualTo(1);
        assertThat(notificaciones(huesped, "CuentaReactivada")).isEqualTo(1);
    }

    @Test
    void reactivarUnaCuentaActivaRespondeConflicto() throws Exception {
        mvc.perform(post("/api/v1/admin/usuarios/{id}/reactivacion", crearUsuario(Rol.HUESPED)).header("Authorization", sesionAdministrador))
                .andExpect(status().isConflict());
    }

    /** ASR-06: AutorizacionIT recorre los tres endpoints con cada rol; aquí, que un anfitrión no deshace su propio bloqueo. */
    @Test
    void elAnfitrionNoPuedeDesbloquearSuPublicacion() throws Exception {
        comoAplicacion.update("UPDATE publicacion SET estado = 'BLOQUEADA' WHERE id = ?", publicacion);

        mvc.perform(post("/api/v1/admin/publicaciones/{id}/desbloqueo", publicacion).header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isForbidden());

        assertThat(texto("SELECT estado FROM publicacion WHERE id = ?", publicacion)).isEqualTo("BLOQUEADA");
    }

    private ResultActions desbloquear() throws Exception {
        return mvc.perform(post("/api/v1/admin/publicaciones/{id}/desbloqueo", publicacion).header("Authorization", sesionAdministrador));
    }

    private UUID reservar(UUID huesped, LocalDate llegada, String estado) {
        return comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, ?, ?, 1, ?) RETURNING id""", UUID.class, huesped, publicacion, llegada, llegada.plusDays(2), estado);
    }

    private String texto(String sql, UUID id) {
        return comoAplicacion.queryForObject(sql, String.class, id);
    }

    private long auditadas(String accion, UUID entidad) {
        return comoAplicacion.queryForObject("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = ?",
                Long.class, entidad, accion);
    }

    private long notificaciones(UUID usuario, String tipo) {
        return comoAplicacion.queryForObject("SELECT count(*) FROM notificacion WHERE usuario_id = ? AND tipo = ?", Long.class, usuario, tipo);
    }
}
