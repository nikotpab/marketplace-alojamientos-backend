package com.inversioneslr.marketplace.administracion.aplicacion;

import com.inversioneslr.marketplace.administracion.EventosAdministracion.ReporteExportado;
import com.inversioneslr.marketplace.administracion.dominio.FiltrosReporte;
import com.inversioneslr.marketplace.administracion.dominio.FormatoReporte;
import com.inversioneslr.marketplace.administracion.dominio.GeneradorReportes;
import com.inversioneslr.marketplace.administracion.dominio.Indicadores;
import com.inversioneslr.marketplace.administracion.dominio.Reporte;
import com.inversioneslr.marketplace.administracion.dominio.RepositorioIndicadores;
import com.inversioneslr.marketplace.administracion.dominio.RepositorioReportes;
import com.inversioneslr.marketplace.administracion.dominio.TipoReporte;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard y reportes: nunca pueden afectar a búsqueda, reserva o cancelación (RNF-10). */
@Service
public class ServicioReportes {

    private static final int TIEMPO_MAX_SEGUNDOS = 10;
    private static final int DIAS_POR_DEFECTO = 30;

    private final RepositorioIndicadores indicadores;
    private final RepositorioReportes reportes;
    private final GeneradorReportes generador;
    private final PublicadorEventos eventos;
    private final Clock reloj;

    public ServicioReportes(RepositorioIndicadores indicadores, RepositorioReportes reportes, GeneradorReportes generador,
                            PublicadorEventos eventos, Clock reloj) {
        this.indicadores = indicadores;
        this.reportes = reportes;
        this.generador = generador;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * CU-29. Sin fechas, muestra los últimos 30 días. Si tarda más de 10 s, la transacción se cancela sin afectar a los
     * demás casos de uso (RNF-10).
     */
    @Transactional(readOnly = true, timeout = TIEMPO_MAX_SEGUNDOS)
    public Indicadores consultarDashboard(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta == null ? LocalDate.now(reloj) : hasta;
        LocalDate inicio = desde == null ? fin.minusDays(DIAS_POR_DEFECTO - 1L) : desde;
        validarPeriodo(inicio, fin);
        return indicadores.calcular(inicio, fin);
    }

    /**
     * CU-30: se genera en otro hilo, con hasta 5 exportaciones a la vez (RNF-08). Cada exportación queda en la auditoría
     * con quién la pidió y sus filtros, que es el historial de exportaciones.
     */
    @Async
    public CompletableFuture<byte[]> exportar(UUID administradorId, TipoReporte tipo, FormatoReporte formato, FiltrosReporte filtros) {
        if (filtros.desde() != null && filtros.hasta() != null) {
            validarPeriodo(filtros.desde(), filtros.hasta());
        }
        Reporte reporte = reportes.consultar(tipo, filtros);
        byte[] contenido = generador.generar(reporte, formato);
        eventos.publicar(new ReporteExportado(administradorId, tipo.name(), formato.name(), reporte.filas().size(),
                String.valueOf(filtros.desde()), String.valueOf(filtros.hasta()), String.valueOf(filtros.ciudad())));
        return CompletableFuture.completedFuture(contenido);
    }

    private static void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            String mensaje = "La fecha inicial es posterior a la final. Elige una fecha igual o anterior.";
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, "El periodo no es válido.", Map.of("desde", mensaje));
        }
    }
}
