package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.auditoria.aplicacion.ServicioAuditoria;
import com.inversioneslr.marketplace.auditoria.aplicacion.VerificacionAuditoria;
import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.Rol;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AuditoriaIT extends PruebaIntegracion {

    @Autowired
    ServicioAuditoria auditoria;

    @Autowired
    VerificacionAuditoria verificacion;

    @Test
    void losRegistrosValidosPasanLaVerificacionAunqueTenganDecimales() {
        UUID entidad = UUID.randomUUID();
        auditoria.atender(new EventoDominio("ReservaConfirmada", null, "reserva", entidad, "EXITO",
                Map.of("total", new BigDecimal("330000.00"), "devolucion", new BigDecimal("150000"), "codigo", "LR-2030-0001",
                        "publicacionId", UUID.randomUUID(), "filas", 3), List.of()));
        UUID registro = comoAplicacion.queryForObject("SELECT id FROM auditoria.evento_auditoria WHERE entidad_id = ?", UUID.class, entidad);

        assertThat(verificacion.verificar()).doesNotContain(registro);
    }

    @Test
    void unRegistroAlteradoSeDetectaYElLatidoReportaLaFalla(CapturedOutput salida) {
        UUID entidad = UUID.randomUUID();
        // Como si alguien cambiara el resultado saltándose los triggers: el hash ya no corresponde.
        comoAplicacion.update("""
                INSERT INTO auditoria.evento_auditoria (accion, entidad, entidad_id, resultado, detalle, hash_registro)
                VALUES ('PagoFallido', 'reserva', ?, 'EXITO', '{}', repeat('0', 64))""", entidad);
        UUID alterado = comoAplicacion.queryForObject("SELECT id FROM auditoria.evento_auditoria WHERE entidad_id = ?", UUID.class, entidad);

        assertThat(verificacion.verificar()).contains(alterado);
        assertThat(verificacion.disponible()).isFalse();
        assertThat(salida.getOut()).contains("Registros de auditoría alterados", alterado.toString());
    }

    @Test
    void registraElEventoConSuHash() {
        UUID entidad = UUID.randomUUID();

        auditoria.atender(new EventoDominio("ReservaConfirmada", null, "reserva", entidad, "EXITO", Map.of("total", 100), List.of()));

        var fila = comoAplicacion.queryForMap("SELECT accion, resultado, detalle::text AS detalle, hash_registro FROM auditoria.evento_auditoria WHERE entidad_id = ?", entidad);
        assertThat(fila).containsEntry("accion", "ReservaConfirmada").containsEntry("resultado", "EXITO");
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
