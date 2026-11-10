package com.inversioneslr.marketplace.cotizacion.infraestructura;

import com.inversioneslr.marketplace.cotizacion.ConfiguracionTarifas;
import com.inversioneslr.marketplace.cotizacion.dominio.RepositorioTarifas;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioTarifasSql implements RepositorioTarifas {

    private final JdbcClient jdbc;

    public RepositorioTarifasSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Un porcentaje sin definir no se cobra ni se descuenta: por ahora es el caso de LIMPIEZA (pendiente 5 del README). */
    @Override
    public ConfiguracionTarifas vigentes() {
        Map<String, Tarifa> porClave = jdbc.sql("SELECT clave, porcentaje, noches_minimas FROM configuracion_tarifa")
                .query((fila, numero) -> new Tarifa(fila.getString("clave"), fila.getBigDecimal("porcentaje"),
                        fila.getObject("noches_minimas", Integer.class)))
                .list().stream()
                .collect(Collectors.toMap(Tarifa::clave, Function.identity()));
        Tarifa estanciaLarga = porClave.get("DESCUENTO_ESTANCIA_LARGA");
        return new ConfiguracionTarifas(
                porcentaje(porClave, "LIMPIEZA"),
                porcentaje(porClave, "SERVICIO"),
                porcentaje(porClave, "DESCUENTO_PROMOCIONAL"),
                porcentaje(porClave, "DESCUENTO_ESTANCIA_LARGA"),
                estanciaLarga == null ? null : estanciaLarga.nochesMinimas());
    }

    @Override
    public void guardar(ConfiguracionTarifas tarifas, UUID modificadoPor) {
        guardar("LIMPIEZA", tarifas.porcentajeLimpieza(), null, modificadoPor);
        guardar("SERVICIO", tarifas.porcentajeServicio(), null, modificadoPor);
        guardar("DESCUENTO_PROMOCIONAL", tarifas.porcentajeDescuento(), null, modificadoPor);
        guardar("DESCUENTO_ESTANCIA_LARGA", tarifas.porcentajeDescuentoEstanciaLarga(), tarifas.nochesEstanciaLarga(), modificadoPor);
    }

    private void guardar(String clave, BigDecimal porcentaje, Integer nochesMinimas, UUID modificadoPor) {
        jdbc.sql("""
                INSERT INTO configuracion_tarifa (clave, porcentaje, noches_minimas, modificado_por)
                VALUES (:clave, :porcentaje, :noches, :modificadoPor)
                ON CONFLICT (clave) DO UPDATE SET
                    porcentaje = EXCLUDED.porcentaje,
                    noches_minimas = EXCLUDED.noches_minimas,
                    modificado_por = EXCLUDED.modificado_por""")
                .param("clave", clave)
                .param("porcentaje", porcentaje)
                .param("noches", nochesMinimas)
                .param("modificadoPor", modificadoPor)
                .update();
    }

    private static BigDecimal porcentaje(Map<String, Tarifa> porClave, String clave) {
        Tarifa tarifa = porClave.get(clave);
        return tarifa == null || tarifa.porcentaje() == null ? BigDecimal.ZERO : tarifa.porcentaje();
    }

    private record Tarifa(String clave, BigDecimal porcentaje, Integer nochesMinimas) {
    }
}
