package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.dominio.Rol;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

class NotificacionesIT extends PruebaIntegracion {

    @Autowired
    PublicadorEventos eventos;

    @Test
    void publicarGuardaAuditoriaYNotificacion() {
        UUID usuario = crearUsuario(Rol.HUESPED);
        UUID entidad = UUID.randomUUID();

        eventos.publicar(evento("ReservaConfirmada", usuario, entidad, List.of(usuario)));

        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ?", entidad)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ?", usuario)).isEqualTo(1);
    }

    @Test
    void siLaAuditoriaFallaSeRevierteTodasLasEscrituras() {
        UUID actorInexistente = UUID.randomUUID();
        UUID entidad = UUID.randomUUID();

        assertThatThrownBy(() -> eventos.publicar(evento("ReservaConfirmada", actorInexistente, entidad, List.of())))
                .isInstanceOf(DataAccessException.class);

        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ?", entidad)).isZero();
    }

    @Test
    void publicarCreaUnaNotificacionPorDestinatarioSinDuplicar() {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID anfitrion = crearUsuario(Rol.ANFITRION);

        eventos.publicar(evento("ReservaConfirmada", huesped, UUID.randomUUID(), List.of(huesped, anfitrion)));
        eventos.publicar(evento("ReservaConfirmada", huesped, UUID.randomUUID(), List.of(huesped, anfitrion)));

        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ?", huesped)).isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ?", anfitrion)).isEqualTo(2);
    }

    @Test
    void elUsuarioConsultaYMarcaSusNotificaciones() throws Exception {
        UUID usuario = crearUsuario(Rol.HUESPED);
        String sesion = sesionDe(usuario, Rol.HUESPED);
        eventos.publicar(evento("ReservaConfirmada", usuario, UUID.randomUUID(), List.of(usuario)));
        eventos.publicar(evento("ReservaCancelada", usuario, UUID.randomUUID(), List.of(usuario)));

        mvc.perform(get("/api/v1/notificaciones").header("Authorization", sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(2))
                .andExpect(jsonPath("$.notificaciones.length()").value(2));

        UUID notificacion = comoAplicacion.queryForObject(
                "SELECT id FROM notificacion WHERE usuario_id = ? AND tipo = 'ReservaConfirmada'", UUID.class, usuario);
        mvc.perform(post("/api/v1/notificaciones/{id}/lectura", notificacion).header("Authorization", sesion))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notificaciones").header("Authorization", sesion)).andExpect(jsonPath("$.noLeidas").value(1));

        mvc.perform(post("/api/v1/notificaciones/lectura").header("Authorization", sesion)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notificaciones").header("Authorization", sesion)).andExpect(jsonPath("$.noLeidas").value(0));
    }

    @Test
    void marcarLaNotificacionDeOtroUsuarioSeDeniegaYSeAudita() throws Exception {
        UUID duena = crearUsuario(Rol.HUESPED);
        UUID intruso = crearUsuario(Rol.HUESPED);
        eventos.publicar(evento("ReservaConfirmada", duena, UUID.randomUUID(), List.of(duena)));
        UUID notificacion = comoAplicacion.queryForObject("SELECT id FROM notificacion WHERE usuario_id = ?", UUID.class, duena);

        mvc.perform(post("/api/v1/notificaciones/{id}/lectura", notificacion).header("Authorization", sesionDe(intruso, Rol.HUESPED)))
                .andExpect(status().isForbidden());

        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE actor_id = ? AND accion = 'AccesoDenegado'", intruso)).isEqualTo(1);
    }

    private static EventoDominio evento(String tipo, UUID actor, UUID entidad, List<UUID> destinatarios) {
        return new EventoDominio(tipo, actor, "reserva", entidad, "EXITO", Map.of(), destinatarios);
    }

    private long contar(String sql, Object valor) {
        return comoAplicacion.queryForObject(sql, Long.class, valor);
    }
}
