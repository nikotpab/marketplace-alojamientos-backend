package com.inversioneslr.marketplace.cotizacion.aplicacion;

import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.cotizacion.api.ConfiguracionTarifas;
import com.inversioneslr.marketplace.cotizacion.api.Cotizacion;
import com.inversioneslr.marketplace.cotizacion.api.FachadaCotizacion;
import com.inversioneslr.marketplace.cotizacion.dominio.RepositorioTarifas;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServicioCotizacion implements FachadaCotizacion {

    private final RepositorioTarifas tarifas;
    private final FachadaPublicaciones publicaciones;
    private final PublicadorEventos eventos;

    public ServicioCotizacion(RepositorioTarifas tarifas, FachadaPublicaciones publicaciones, PublicadorEventos eventos) {
        this.tarifas = tarifas;
        this.publicaciones = publicaciones;
        this.eventos = eventos;
    }

    @Override
    public Cotizacion cotizar(UUID publicacionId, LocalDate llegada, LocalDate salida, List<UUID> serviciosAdicionales) {
        throw new UnsupportedOperationException("CU-16");
    }

    @Override
    public ConfiguracionTarifas consultarTarifas() {
        throw new UnsupportedOperationException("CU-28");
    }

    @Override
    public void actualizarTarifas(UUID administradorId, ConfiguracionTarifas nuevasTarifas) {
        throw new UnsupportedOperationException("CU-28");
    }
}
