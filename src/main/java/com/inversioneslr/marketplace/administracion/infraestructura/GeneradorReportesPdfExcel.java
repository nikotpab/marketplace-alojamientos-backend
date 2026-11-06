package com.inversioneslr.marketplace.administracion.infraestructura;

import com.inversioneslr.marketplace.administracion.dominio.FormatoReporte;
import com.inversioneslr.marketplace.administracion.dominio.GeneradorReportes;
import com.inversioneslr.marketplace.administracion.dominio.TipoReporte;
import org.springframework.stereotype.Component;

@Component
public class GeneradorReportesPdfExcel implements GeneradorReportes {

    @Override
    public byte[] generar(TipoReporte tipo, FormatoReporte formato) {
        throw new UnsupportedOperationException("CU-30: generar PDF o Excel de hasta 10.000 registros");
    }
}
