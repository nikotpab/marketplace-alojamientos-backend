/**
 * Cotización y configuración de tarifas (RF-17, RF-32).
 *
 * <p>SCRUM-145: las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Cotización",
        allowedDependencies = {"compartido", "publicaciones"})
package com.inversioneslr.marketplace.cotizacion;
