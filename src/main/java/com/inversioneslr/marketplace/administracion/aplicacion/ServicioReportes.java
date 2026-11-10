package com.inversioneslr.marketplace.administracion.aplicacion;

import com.inversioneslr.marketplace.administracion.dominio.FormatoReporte;
import com.inversioneslr.marketplace.administracion.dominio.GeneradorReportes;
import com.inversioneslr.marketplace.administracion.dominio.Indicadores;
import com.inversioneslr.marketplace.administracion.dominio.RepositorioIndicadores;
import com.inversioneslr.marketplace.administracion.dominio.TipoReporte;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard y reportes: nunca pueden afectar a búsqueda, reserva o cancelación (RNF-10). */
@Service
public class ServicioReportes {

    private static final int TIEMPO_MAX_SEGUNDOS = 10;

    private final RepositorioIndicadores indicadores;
    private final GeneradorReportes generador;
    private final PublicadorEventos eventos;

    public ServicioReportes(RepositorioIndicadores indicadores, GeneradorReportes generador, PublicadorEventos eventos) {
        this.indicadores = indicadores;
        this.generador = generador;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true, timeout = TIEMPO_MAX_SEGUNDOS)
    public Indicadores consultarDashboard() {
        throw new UnsupportedOperationException("CU-29");
    }

    /** CU-30: registra en auditoría quién exportó y cuándo. */
    @Async
    public CompletableFuture<byte[]> exportar(UUID administradorId, TipoReporte tipo, FormatoReporte formato) {
        throw new UnsupportedOperationException("CU-30");
    }
}
