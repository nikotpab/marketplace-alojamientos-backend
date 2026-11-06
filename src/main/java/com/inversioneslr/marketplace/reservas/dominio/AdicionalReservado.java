package com.inversioneslr.marketplace.reservas.dominio;

import java.math.BigDecimal;
import java.util.UUID;

/** Servicio adicional con el costo aplicado al confirmar. */
public record AdicionalReservado(UUID servicioAdicionalId, BigDecimal costoAplicado) {
}
