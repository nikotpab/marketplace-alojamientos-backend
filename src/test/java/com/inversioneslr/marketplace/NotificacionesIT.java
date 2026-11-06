package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static EventoDominio evento(String tipo, UUID actor, UUID entidad, List<UUID> destinatarios) {
        return new EventoDominio(tipo, actor, "reserva", entidad, "EXITO", Map.of(), destinatarios);
    }

    private long contar(String sql, Object valor) {
        return comoAplicacion.queryForObject(sql, Long.class, valor);
    }
}
