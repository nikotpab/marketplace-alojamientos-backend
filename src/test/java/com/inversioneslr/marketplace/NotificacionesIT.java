package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.inversioneslr.marketplace.compartido.dominio.SuscriptorAsincrono;
import com.inversioneslr.marketplace.compartido.infraestructura.ProcesadorEventos;
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
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    PlatformTransactionManager transacciones;

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
        ProcesadorEventos procesador = new ProcesadorEventos(List.of(fallido), jdbc, json, transacciones);

        procesador.procesarPendientes();

        String consulta = "SELECT estado || ':' || intentos FROM evento_pendiente WHERE payload->>'entidadId' = ?";
        assertThat(comoAplicacion.queryForObject(consulta, String.class, entidad.toString())).isEqualTo("PENDIENTE:1");

        for (int ciclo = 0; ciclo < 4; ciclo++) {
            procesador.procesarPendientes();
        }

        assertThat(comoAplicacion.queryForObject(consulta, String.class, entidad.toString())).isEqualTo("FALLIDO:5");
    }

    private static EventoDominio evento(String tipo, UUID actor, UUID entidad, List<UUID> destinatarios) {
        return new EventoDominio(tipo, actor, "reserva", entidad, "EXITO", Map.of(), destinatarios);
    }

    private long contar(String sql, Object valor) {
        return comoAplicacion.queryForObject(sql, Long.class, valor);
    }
}
