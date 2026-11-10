package com.inversioneslr.marketplace.pagos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioPagos {

    /** Registra el cobro como PENDIENTE antes de llamar a la pasarela. Un reintento con la misma clave no lo duplica. */
    void registrarIntento(String clave, BigDecimal monto);

    /** Guarda el pago aprobado; si ya estaba el intento con esa clave, lo completa. */
    void guardar(Pago pago);

    void marcarRechazado(String clave);

    /** Un cobro PENDIENTE que se devolvió porque no llegó a tener reserva. */
    void marcarDevueltoSinReserva(String clave, String referencia);

    List<Pago> listarPendientesCreadosAntesDe(Instant momento);

    /**
     * Pasa el pago aprobado de la reserva a DEVUELTO. Si dos solicitudes llegan a la vez, solo una lo consigue.
     *
     * @return la referencia del pago, o vacío si no había un pago aprobado
     */
    Optional<String> marcarDevuelto(UUID reservaId);

    /** Deshace {@link #marcarDevuelto} cuando la pasarela no procesó la devolución. */
    void marcarAprobado(UUID reservaId);
}
