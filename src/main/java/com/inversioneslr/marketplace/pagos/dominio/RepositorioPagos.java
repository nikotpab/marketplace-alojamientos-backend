package com.inversioneslr.marketplace.pagos.dominio;

import java.util.Optional;
import java.util.UUID;

public interface RepositorioPagos {

    void guardar(Pago pago);

    /**
     * Pasa el pago aprobado de la reserva a DEVUELTO. Si dos solicitudes llegan a la vez, solo una lo consigue.
     *
     * @return la referencia del pago, o vacío si no había un pago aprobado
     */
    Optional<String> marcarDevuelto(UUID reservaId);

    /** Deshace {@link #marcarDevuelto} cuando la pasarela no procesó la devolución. */
    void marcarAprobado(UUID reservaId);
}
