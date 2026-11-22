/**
 * Cotización y configuración de tarifas.
 *
 * <p>Las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Cotización",
        allowedDependencies = {"compartido", "publicaciones"})
package com.inversioneslr.marketplace.cotizacion;
