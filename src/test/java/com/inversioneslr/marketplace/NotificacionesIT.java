package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.SuscriptorAsincrono;
import com.inversioneslr.marketplace.compartido.infraestructura.ProcesadorEventos;
import com.inversioneslr.marketplace.notificaciones.dominio.Notificacion;
import com.inversioneslr.marketplace.notificaciones.dominio.RepositorioNotificaciones;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

class NotificacionesIT extends PruebaIntegracion {

    @Autowired
    PublicadorEventos eventos;

    @Autowired
    ProcesadorEventos procesador;

    @Autowired
    RepositorioNotificaciones notificaciones;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    PlatformTransactionManager transacciones;

    @Autowired
    Clock reloj;

    @Test
    void publicarGuardaAuditoriaYEventoPendiente() {
        UUID usuario = crearUsuario(Rol.HUESPED);
        UUID entidad = UUID.randomUUID();

        eventos.publicar(evento("ReservaConfirmada", usuario, entidad, List.of(usuario)));

        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ?", entidad)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM evento_pendiente WHERE payload->>'entidadId' = ?", entidad.toString())).isEqualTo(1);
    }

    @Test
    void siLaAuditoriaFallaNoQuedaEventoPendiente() {
        UUID actorInexistente = UUID.randomUUID();
        UUID entidad = UUID.randomUUID();

        assertThatThrownBy(() -> eventos.publicar(evento("ReservaConfirmada", actorInexistente, entidad, List.of())))
                .isInstanceOf(DataAccessException.class);

        assertThat(contar("SELECT count(*) FROM evento_pendiente WHERE payload->>'entidadId' = ?", entidad.toString())).isZero();
    }

    @Test
    void elProcesadorReintentaUnEventoFallidoHastaElLimite() {
        UUID usuario = crearUsuario(Rol.HUESPED);
        UUID entidad = UUID.randomUUID();
        eventos.publicar(evento("ReservaConfirmada", usuario, entidad, List.of(usuario)));
        SuscriptorAsincrono fallido = evento -> {
            throw new IllegalStateException("Componente caído");
        };
        ProcesadorEventos procesador = new ProcesadorEventos(List.of(fallido), jdbc, json, transacciones, reloj);

        procesador.procesarPendientes();

        String consulta = "SELECT estado || ':' || intentos FROM evento_pendiente WHERE payload->>'entidadId' = ?";
        assertThat(comoAplicacion.queryForObject(consulta, String.class, entidad.toString())).isEqualTo("PENDIENTE:1");

        for (int ciclo = 0; ciclo < 4; ciclo++) {
            procesador.procesarPendientes();
        }

        assertThat(comoAplicacion.queryForObject(consulta, String.class, entidad.toString())).isEqualTo("FALLIDO:5");
    }

    @Test
    void entregarUnEventoCreaUnaNotificacionPorDestinatarioSinDuplicar() {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        eventos.publicar(evento("ReservaConfirmada", huesped, UUID.randomUUID(), List.of(huesped, anfitrion)));

        procesador.procesarPendientes();
        procesador.procesarPendientes();

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
        eventos.publicar(evento("ReservaConfirmada", usuario, UUID.randomUUID(), List.of(usuario)));
        eventos.publicar(evento("ReservaCancelada", usuario, UUID.randomUUID(), List.of(usuario)));
        procesador.procesarPendientes();

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
        procesador.procesarPendientes();
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
