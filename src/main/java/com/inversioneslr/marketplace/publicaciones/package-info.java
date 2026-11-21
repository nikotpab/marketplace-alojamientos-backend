/**
 * Publicaciones, su revisión y su disponibilidad.
 *
 * <p>Las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Publicaciones",
        allowedDependencies = {"compartido", "usuarios"})
package com.inversioneslr.marketplace.publicaciones;
