/**
 * Confirmación de reservas con idempotencia e historial (RF-18 a RF-20, ADR-03, ADR-04).
 *
 * <p>SCRUM-145: las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Reservas",
        allowedDependencies = {"compartido", "cotizacion", "pagos", "publicaciones", "usuarios"})
package com.inversioneslr.marketplace.reservas;
