/**
 * Búsqueda y detalle de alojamientos. Es un modelo de lectura.
 *
 * <p>Las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Búsqueda",
        allowedDependencies = {"compartido", "resenas"})
package com.inversioneslr.marketplace.busqueda;
