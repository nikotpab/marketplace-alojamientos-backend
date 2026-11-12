/**
 * Publicaciones, su revisión y su disponibilidad (RF-06 a RF-14).
 *
 * <p>SCRUM-145: las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Publicaciones",
        allowedDependencies = {"compartido", "usuarios"})
package com.inversioneslr.marketplace.publicaciones;
