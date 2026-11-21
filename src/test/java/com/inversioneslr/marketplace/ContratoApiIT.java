package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * El spec OpenAPI en vivo debe coincidir byte a byte con docs/openapi.json.
 * Para regenerar el archivo: mvn verify -Dopenapi.actualizar=true
 */
class ContratoApiIT extends PruebaIntegracion {

    private static final Path CONTRATO = Path.of("docs/openapi.json");

    @Test
    void contratoVersionadoCoincideConArchivoEnRepo() throws Exception {
        String spec = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ObjectMapper mapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        String normalizado = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(ordenarRecursivamente(mapper.readValue(spec, Object.class)))
                .replace("\r\n", "\n");

        if ("true".equals(System.getProperty("openapi.actualizar"))) {
            Files.writeString(CONTRATO, normalizado + "\n", StandardCharsets.UTF_8);
            return;
        }

        if (!Files.exists(CONTRATO)) {
            fail("docs/openapi.json no existe. Genera con: mvn verify -Dopenapi.actualizar=true");
        }

        String guardado = Files.readString(CONTRATO, StandardCharsets.UTF_8)
                .replace("\r\n", "\n").stripTrailing();
        if (!normalizado.equals(guardado)) {
            fail("El contrato cambió: regenera con mvn verify -Dopenapi.actualizar=true y revisa el diff");
        }
    }

    /**
     * Normalización canónica: ordena claves de objetos (TreeMap) y elementos de arrays
     * por su representación de cadena, para que el spec sea estable entre ejecuciones de JVM
     * independientemente del orden de reflexión de springdoc.
     */
    @SuppressWarnings("unchecked")
    private static Object ordenarRecursivamente(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            ((Map<String, Object>) map).forEach((k, v) -> sorted.put(k, ordenarRecursivamente(v)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return ((List<Object>) list).stream()
                    .map(ContratoApiIT::ordenarRecursivamente)
                    .sorted(Comparator.comparing(Object::toString))
                    .toList();
        }
        return value;
    }
}
