package com.inversioneslr.marketplace.reservas.dominio;

/** Una reserva del historial, con el título del alojamiento para mostrarla en la lista. */
public record ReservaConAlojamiento(Reserva reserva, String alojamiento) {
}
