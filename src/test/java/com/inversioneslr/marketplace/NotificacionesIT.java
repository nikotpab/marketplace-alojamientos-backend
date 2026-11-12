package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.infraestructura.ProcesadorEventos;
import com.inversioneslr.marketplace.notificaciones.dominio.Notificacion;
import com.inversioneslr.marketplace.notificaciones.dominio.RepositorioNotificaciones;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

class NotificacionesIT extends PruebaIntegracion {

    @Autowired
    PublicadorEventos eventos;

    @Autowired
    ProcesadorEventos procesador;

    @Autowired
    RepositorioNotificaciones notificaciones;


    @Test
    void publicarGuardaLaAuditoriaYRegistraElEvento() {
        UUID usuario = crearUsuario(Rol.HUESPED);
        UUID entidad = UUID.randomUUID();

        eventos.publicar(evento("ReservaConfirmada", usuario, entidad, List.of(usuario)));

        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ?", entidad)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM event_publication WHERE serialized_event::jsonb ->> 'entidadId' = ?",
                entidad.toString())).isEqualTo(1);
    }

    @Test
    void siLaAuditoriaFallaNoQuedaElEvento() {
        UUID actorInexistente = UUID.randomUUID();
        UUID entidad = UUID.randomUUID();

        var conActorInexistente = evento("ReservaConfirmada", actorInexistente, entidad, List.of());

        assertThatThrownBy(() -> eventos.publicar(conActorInexistente))
                .isInstanceOf(DataAccessException.class);

        assertThat(contar("SELECT count(*) FROM event_publication WHERE serialized_event::jsonb ->> 'entidadId' = ?",
                entidad.toString())).isZero();
    }

    @Test
    void unaEntregaFallidaSeReintentaHastaElLimite() {
        UUID usuario = crearUsuario(Rol.HUESPED);
        UUID entidad = UUID.randomUUID();
        UUID destinatarioInexistente = UUID.randomUUID();

        eventos.publicar(evento("ReservaConfirmada", usuario, entidad, List.of(destinatarioInexistente)));
        esperarEstado(entidad, "FAILED:1");

        for (int intento = 2; intento <= 5; intento++) {
            procesador.reintentarFallidos();
            esperarEstado(entidad, "FAILED:" + intento);
        }
        procesador.reintentarFallidos();

        assertThat(estado(entidad)).isEqualTo("FAILED:5");
    }

    @Test
    void entregarUnEventoCreaUnaNotificacionPorDestinatario() {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID entidad = UUID.randomUUID();

        eventos.publicar(evento("ReservaConfirmada", huesped, entidad, List.of(huesped, anfitrion)));
        esperarEventosDe(entidad);

        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ?", huesped)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ?", anfitrion)).isEqualTo(1);
    }

    @Test
    void entregarOtraVezUnEventoNoDuplicaNiDesmarcaLaNotificacion() {
        UUID usuario = crearUsuario(Rol.HUESPED);
        EventoDominio evento = evento("ReservaConfirmada", usuario, UUID.randomUUID(), List.of(usuario));
        Notificacion notificacion = Notificacion.nueva(evento.id(), usuario, evento.tipo(), reloj.instant());
        notificaciones.guardar(notificacion);
        notificaciones.guardar(notificacion.marcarLeida());

        notificaciones.guardar(Notificacion.nueva(evento.id(), usuario, evento.tipo(), reloj.instant()));

        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ?", usuario)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ? AND leida", usuario)).isEqualTo(1);
    }

    @Test
    void elUsuarioConsultaYMarcaSusNotificaciones() throws Exception {
        UUID usuario = crearUsuario(Rol.HUESPED);
        String sesion = sesionDe(usuario, Rol.HUESPED);
        UUID confirmada = UUID.randomUUID();
        UUID cancelada = UUID.randomUUID();
        eventos.publicar(evento("ReservaConfirmada", usuario, confirmada, List.of(usuario)));
        eventos.publicar(evento("ReservaCancelada", usuario, cancelada, List.of(usuario)));
        esperarEventosDe(confirmada);
        esperarEventosDe(cancelada);

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
        UUID entidad = UUID.randomUUID();
        eventos.publicar(evento("ReservaConfirmada", duena, entidad, List.of(duena)));
        esperarEventosDe(entidad);
        UUID notificacion = comoAplicacion.queryForObject("SELECT id FROM notificacion WHERE usuario_id = ?", UUID.class, duena);

        mvc.perform(post("/api/v1/notificaciones/{id}/lectura", notificacion).header("Authorization", sesionDe(intruso, Rol.HUESPED)))
                .andExpect(status().isForbidden());

        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE actor_id = ? AND accion = 'AccesoDenegado'", intruso)).isEqualTo(1);
    }

    private static EventoDominio evento(String tipo, UUID actor, UUID entidad, List<UUID> destinatarios) {
        return new EventoDePrueba(tipo, actor, "reserva", entidad, "EXITO", Map.of(), destinatarios);
    }

    /** Estado e intentos de la entrega a las notificaciones, por ejemplo "FAILED:2". */
    private String estado(UUID entidad) {
        return comoAplicacion.queryForObject("""
                SELECT status || ':' || completion_attempts FROM event_publication
                WHERE serialized_event::jsonb ->> 'entidadId' = ?""", String.class, entidad.toString());
    }

    private void esperarEstado(UUID entidad, String esperado) {
        await().until(() -> esperado.equals(estado(entidad)));
    }

    private long contar(String sql, Object valor) {
        return comoAplicacion.queryForObject(sql, Long.class, valor);
    }
}
