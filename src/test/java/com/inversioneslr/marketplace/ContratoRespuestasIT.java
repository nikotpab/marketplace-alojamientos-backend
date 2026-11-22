package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import com.inversioneslr.marketplace.compartido.Rol;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Valida que las respuestas reales cumplan el contrato de docs/openapi.json.
 * Solo las operaciones con respuesta documentada en el spec (200, 201, 204) se validan con el
 * validador. Los errores (400, 401, 404) no están en el spec, por lo que solo se verifica el
 * código de estado.
 */
class ContratoRespuestasIT extends PruebaIntegracion {

    private static OpenApiInteractionValidator validador;

    private UUID anfitrion;
    private UUID publicacion;
    private UUID huesped;
    private String tokenHuesped;
    private UUID admin;
    private String tokenAdmin;

    @BeforeAll
    static void cargarValidador() throws Exception {
        String spec = Files.readString(Path.of("docs/openapi.json"), StandardCharsets.UTF_8);
        validador = OpenApiInteractionValidator.createForInlineApiSpecification(spec).build();
    }

    @BeforeEach
    void preparar() {
        anfitrion = crearUsuario(Rol.ANFITRION);
        publicacion = crearPublicacionActiva(anfitrion);
        huesped = crearUsuario(Rol.HUESPED);
        tokenHuesped = sesionDe(huesped, Rol.HUESPED);
        admin = crearUsuario(Rol.ADMINISTRADOR);
        tokenAdmin = sesionDe(admin, Rol.ADMINISTRADOR);
    }

    // ── Inicio de sesión ──────────────────────────────────────────────────────

    @Test
    void login_respuesta200_cumpleElContrato() throws Exception {
        String correo = UUID.randomUUID() + "@contrato.co";
        String codigo = codigoDeRegistro(correo);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Laura","apellido":"Gómez","correo":"%s","telefono":"3001234567",\
"contrasena":"Contrasena-1","rol":"HUESPED","codigo":"%s"}""".formatted(correo, codigo)))
                .andExpect(status().isCreated());

        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"contrasena\":\"Contrasena-1\"}".formatted(correo)))
                .andExpect(status().isOk())
                .andReturn();
        validar(result);
    }

    @Test
    void login_credencialesErroneas_401() throws Exception {
        // 401 no está en el spec → solo verificamos el código de estado
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"noexiste@test.co\",\"contrasena\":\"Contrasena-1\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Búsqueda ──────────────────────────────────────────────────────────────

    @Test
    void busqueda_respuesta200_cumpleElContrato() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/alojamientos")
                .header("Authorization", tokenHuesped)
                .param("ciudad", "Cartagena"))
                .andExpect(status().isOk())
                .andReturn();
        validar(result);
    }

    // ── Detalle ───────────────────────────────────────────────────────────────

    @Test
    void detalle_respuesta200_cumpleElContrato() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/alojamientos/{id}", publicacion))
                .andExpect(status().isOk())
                .andReturn();
        validar(result);
    }

    @Test
    void detalle_noExiste_404() throws Exception {
        // 404 no está en el spec → solo verificamos el código de estado
        mvc.perform(get("/api/v1/alojamientos/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── Cotización ────────────────────────────────────────────────────────────

    @Test
    void cotizacion_respuesta200_cumpleElContrato() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/cotizaciones")
                .header("Authorization", tokenHuesped)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"publicacionId":"%s","llegada":"2030-06-10","salida":"2030-06-13",\
"huespedes":2,"adicionales":[]}""".formatted(publicacion)))
                .andExpect(status().isOk())
                .andReturn();
        validar(result);
    }

    @Test
    void cotizacion_cuerpoInvalido_400() throws Exception {
        // 400 no está en el spec → solo verificamos el código de estado
        mvc.perform(post("/api/v1/cotizaciones")
                .header("Authorization", tokenHuesped)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── Reserva y cancelación ─────────────────────────────────────────────────

    @Test
    void reserva_respuesta201_cumpleElContrato() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/reservas")
                .header("Authorization", tokenHuesped)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"publicacionId":"%s","llegada":"2030-07-01","salida":"2030-07-04",\
"huespedes":2,"adicionales":[]}""".formatted(publicacion)))
                .andExpect(status().isCreated())
                .andReturn();
        validar(result);
    }

    @Test
    void cancelacion_respuesta200_cumpleElContrato() throws Exception {
        String idReserva = JsonPath.read(
                mvc.perform(post("/api/v1/reservas")
                        .header("Authorization", tokenHuesped)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publicacionId":"%s","llegada":"2030-08-01","salida":"2030-08-04",\
"huespedes":2,"adicionales":[]}""".formatted(publicacion)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");

        MvcResult result = mvc.perform(post("/api/v1/reservas/{reservaId}/cancelacion", idReserva)
                .header("Authorization", tokenHuesped)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Cambio de planes\"}"))
                .andExpect(status().isOk())
                .andReturn();
        validar(result);
    }

    @Test
    void reserva_solapada_409() throws Exception {
        // 409 no está en el spec → solo verificamos el código de estado
        String cuerpo = """
                {"publicacionId":"%s","llegada":"2030-09-10","salida":"2030-09-13",\
"huespedes":2,"adicionales":[]}""".formatted(publicacion);
        mvc.perform(post("/api/v1/reservas")
                .header("Authorization", tokenHuesped)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/reservas")
                .header("Authorization", tokenHuesped)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.replace("2030-09-10", "2030-09-11").replace("2030-09-13", "2030-09-15")))
                .andExpect(status().isConflict());
    }

    // ── Moderación ────────────────────────────────────────────────────────────

    @Test
    void bloqueoPublicacion_respuesta204_cumpleElContrato() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/publicaciones/{id}/bloqueo", publicacion)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Contenido inapropiado\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        validar(result);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void validar(MvcResult result) {
        MockHttpServletRequest req = result.getRequest();
        MockHttpServletResponse resp = result.getResponse();

        SimpleResponse.Builder respBuilder = SimpleResponse.Builder.status(resp.getStatus());
        resp.getHeaderNames().forEach(name ->
                resp.getHeaders(name).forEach(val -> respBuilder.withHeader(name, val)));
        try {
            String respBody = resp.getContentAsString(StandardCharsets.UTF_8);
            if (!respBody.isEmpty()) {
                respBuilder.withBody(respBody);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Request.Method method = Request.Method.valueOf(req.getMethod());
        ValidationReport report = validador.validateResponse(req.getRequestURI(), method, respBuilder.build());
        assertThat(report.hasErrors())
                .as("Respuesta de %s %s no cumple el contrato:\n%s",
                        req.getMethod(), req.getRequestURI(),
                        SimpleValidationReportFormat.getInstance().apply(report))
                .isFalse();
    }
}
