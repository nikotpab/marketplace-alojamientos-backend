package com.inversioneslr.marketplace.administracion.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record Indicadores(long usuarios, long publicacionesActivas, Map<String, Long> reservasPorEstado,
                          BigDecimal ingresosEstimados, List<String> ciudadesConMayorDemanda) {
}
