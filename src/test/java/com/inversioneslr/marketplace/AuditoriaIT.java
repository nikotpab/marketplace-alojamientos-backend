package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.auditoria.aplicacion.ServicioAuditoria;
import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.Rol;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuditoriaIT extends PruebaIntegracion {

    @Autowired
    ServicioAuditoria auditoria;

    @Test
    void registraElEventoConSuHash() {
        UUID entidad = UUID.randomUUID();

        auditoria.atender(new EventoDominio("ReservaConfirmada", null, "reserva", entidad, "EXITO", Map.of("total", 100), List.of()));

        var fila = comoAplicacion.queryForMap("SELECT accion, resultado, detalle::text AS detalle, hash_registro FROM auditoria.evento_auditoria WHERE entidad_id = ?", entidad);
        assertThat(fila.get("accion")).isEqualTo("ReservaConfirmada");
        assertThat(fila.get("resultado")).isEqualTo("EXITO");
        assertThat(fila.get("detalle").toString()).contains("\"total\"");
        assertThat(fila.get("hash_registro").toString()).hasSize(64);
    }

    @Test
    void administradorConsultaPorTipoDeEvento() throws Exception {
        UUID administrador = crearUsuario(Rol.ADMINISTRADOR);
        String tipo = "Prueba" + UUID.randomUUID().toString().substring(0, 8);
        auditoria.atender(new EventoDominio(tipo, administrador, "reserva", UUID.randomUUID(), "EXITO", Map.of(), List.of()));
        auditoria.atender(new EventoDominio("OtroEvento", administrador, "reserva", UUID.randomUUID(), "EXITO", Map.of(), List.of()));

        mvc.perform(get("/api/v1/admin/auditoria").param("accion", tipo).header("Authorization", sesionDe(administrador, Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accion").value(tipo))
                .andExpect(jsonPath("$[0].actor").value("Nombre Apellido"))
                .andExpect(jsonPath("$[0].rol").value("ADMINISTRADOR"));
    }

    @Test
    void rangoDeFechasInvalidoSeRechazaIndicandoElCampo() throws Exception {
        String administrador = sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR);

        mvc.perform(get("/api/v1/admin/auditoria").param("desde", "2030-02-01").param("hasta", "2030-01-01").header("Authorization", administrador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.desde").exists());
    }

    @Test
    void soloElAdministradorConsultaLaAuditoria() throws Exception {
        String huesped = sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED);

        mvc.perform(get("/api/v1/admin/auditoria").header("Authorization", huesped)).andExpect(status().isForbidden());
    }
}
