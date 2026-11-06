package com.inversioneslr.marketplace.compartido.infraestructura;

import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/** Enmascara tokens y datos personales en todos los logs JSON (RNF-25). */
public class EnmascaradorLogs implements StructuredLoggingJsonMembersCustomizer<Object> {

    private static final Pattern SENSIBLE = Pattern.compile(
            "[\\w.+-]+@[\\w.-]+\\.\\w+"          // correos
            + "|Bearer\\s+[\\w.~+/=-]+"          // tokens de sesión
            + "|\\b[spr]k_\\w+"                  // claves de Stripe
            + "|\\b\\d{13,19}\\b");              // números de tarjeta

    @Override
    public void customize(JsonWriter.Members<Object> miembros) {
        miembros.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, EnmascaradorLogs::enmascarar));
    }

    static String enmascarar(String valor) {
        return valor == null ? null : SENSIBLE.matcher(valor).replaceAll("***");
    }
}
