package com.inversioneslr.marketplace.pagos.aplicacion;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.ReservaId;
import com.inversioneslr.marketplace.pagos.EventosPagos.CobroRevertido;
import com.inversioneslr.marketplace.pagos.FachadaPagos;
import com.inversioneslr.marketplace.pagos.dominio.Pago;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import com.inversioneslr.marketplace.pagos.dominio.RepositorioPagos;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** CU-19. */
@Service
public class ServicioPagos implements FachadaPagos {

    private static final Logger log = LoggerFactory.getLogger(ServicioPagos.class);

    /** Una confirmación tarda segundos: a los 10 min, un cobro pendiente ya no va a tener reserva. */
    private static final Duration ESPERA_ANTES_DE_REVERTIR = Duration.ofMinutes(10);

    private final PasarelaPagos pasarela;
    private final RepositorioPagos pagos;
    private final PublicadorEventos eventos;
    private final Clock reloj;

    public ServicioPagos(PasarelaPagos pasarela, RepositorioPagos pagos, PublicadorEventos eventos, Clock reloj) {
        this.pasarela = pasarela;
        this.pagos = pagos;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** Registra el intento antes de llamar a la pasarela, para que un cobro nunca quede sin rastro (RNF-12). */
    @Override
    public String cobrar(BigDecimal monto, String claveSolicitud) {
        pagos.registrarIntento(claveSolicitud, monto);
        try {
            return pasarela.cobrar(monto, claveSolicitud);
        } catch (ExcepcionNegocio error) {
            if (error.tipo() == ExcepcionNegocio.Tipo.PAGO_RECHAZADO) {
                pagos.marcarRechazado(claveSolicitud);
            }
            throw error;
        }
    }

    /** Si la devolución falla, el cobro sigue PENDIENTE y la reconciliación lo devuelve después. */
    @Override
    public void devolverCobroSinReserva(String claveSolicitud, String referencia, BigDecimal monto) {
        pasarela.devolver(referencia, monto);
        pagos.marcarDevueltoSinReserva(claveSolicitud, referencia);
    }

    /** Marca el pago antes de llamar a la pasarela: así dos cancelaciones simultáneas no devuelven dos veces. */
    @Override
    public void devolverPagoDeReserva(ReservaId reservaId, BigDecimal monto) {
        String referencia = pagos.marcarDevuelto(reservaId.valor())
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "La reserva no tiene un pago aprobado que devolver."));
        try {
            pasarela.devolver(referencia, monto);
        } catch (RuntimeException e) {
            pagos.marcarAprobado(reservaId.valor());
            throw e;
        }
    }

    /** @param reservaId null si el pago todavía no tiene reserva, como antes de SCRUM-144 */
    @Override
    public void registrarPago(ReservaId reservaId, String referencia, String claveSolicitud, BigDecimal monto) {
        pagos.guardar(new Pago(UUID.randomUUID(), reservaId == null ? null : reservaId.valor(), referencia, claveSolicitud, monto, Pago.Estado.APROBADO));
    }

    /**
     * RNF-12 · SCRUM-59: revierte cada 5 min los cobros que llevan 10 min PENDIENTES, así que quedan resueltos en
     * máximo 15 min. Le pide el cobro a la pasarela con la misma clave: si ya se hizo, devuelve el mismo pago sin
     * cobrar otra vez (ADR-04). Luego lo devuelve completo.
     */
    @Scheduled(cron = "${marketplace.pagos.reconciliacion-cron:0 */5 * * * *}")
    public void revertirCobrosSinReserva() {
        pagos.listarPendientesCreadosAntesDe(reloj.instant().minus(ESPERA_ANTES_DE_REVERTIR)).forEach(this::revertir);
    }

    private void revertir(Pago pago) {
        try {
            String referencia = pasarela.cobrar(pago.monto(), pago.claveIdempotencia());
            pasarela.devolver(referencia, pago.monto());
            pagos.marcarDevueltoSinReserva(pago.claveIdempotencia(), referencia);
            eventos.publicar(new CobroRevertido(pago.id(), pago.monto(), referencia));
        } catch (ExcepcionNegocio error) {
            if (error.tipo() == ExcepcionNegocio.Tipo.PAGO_RECHAZADO) {
                pagos.marcarRechazado(pago.claveIdempotencia());
            } else {
                log.warn("No se pudo revertir el cobro {}; se reintenta en el siguiente ciclo", pago.id(), error);
            }
        }
    }
}
