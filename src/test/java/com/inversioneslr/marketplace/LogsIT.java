package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

@ExtendWith(OutputCaptureExtension.class)
class LogsIT extends PruebaIntegracion {

    @Autowired
    PublicadorEventos eventos;

    @Test
    void cadaEventoQuedaEnLosLogsConCamposFiltrablesYCorrelationId(CapturedOutput salida) throws Exception {
        String correo = UUID.randomUUID() + "@prueba.co";

        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Laura","apellido":"Gómez","correo":"%s","telefono":"3001234567","contrasena":"Contrasena-de-prueba-1","rol":"HUESPED"}"""
                .formatted(correo)));

        assertThat(salida.getOut()).contains("\"tipoEvento\":\"UsuarioRegistrado\"", "\"usuario\":", "\"correlationId\":");
        assertThat(salida.getOut()).doesNotContain("Contrasena-de-prueba-1", correo);
    }

    @Test
    void unEventoDelSistemaTambienSeRegistra(CapturedOutput salida) {
        eventos.publicar(new EventoDominio("EventoDelSistema", null, "reserva", UUID.randomUUID(), "EXITO", Map.of(), List.of()));

        assertThat(salida.getOut()).contains("\"tipoEvento\":\"EventoDelSistema\"");
    }

    @Test
    void losLogsEnmascaranLosDatosSensibles(CapturedOutput salida) {
        LoggerFactory.getLogger(LogsIT.class).info("Contacto laura@correo.co con Authorization: Bearer abc.def.ghi");

        assertThat(salida.getOut()).doesNotContain("laura@correo.co", "abc.def.ghi");
    }
}
