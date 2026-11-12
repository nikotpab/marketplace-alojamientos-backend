/**
 * Reseñas de estadías completadas (RF-25, RF-26).
 *
 * <p>SCRUM-145: las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Reseñas",
        allowedDependencies = {"compartido", "reservas", "usuarios"})
package com.inversioneslr.marketplace.resenas;
