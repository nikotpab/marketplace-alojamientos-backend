/**
 * Reseñas de estadías completadas.
 *
 * <p>Las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Reseñas",
        allowedDependencies = {"compartido", "reservas", "usuarios"})
package com.inversioneslr.marketplace.resenas;
