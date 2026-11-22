/**
 * Confirmación de reservas con idempotencia e historial.
 *
 * <p>Las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Reservas",
        allowedDependencies = {"compartido", "cotizacion", "pagos", "publicaciones", "usuarios"})
package com.inversioneslr.marketplace.reservas;
