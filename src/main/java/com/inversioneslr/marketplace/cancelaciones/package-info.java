/**
 * Cancelación con la política de la publicación y devolución (RF-22, RF-23).
 *
 * <p>SCRUM-145: las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Cancelaciones",
        allowedDependencies = {"compartido", "pagos", "publicaciones", "reservas"})
package com.inversioneslr.marketplace.cancelaciones;
