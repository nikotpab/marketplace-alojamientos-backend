/**
 * Notificaciones dentro de la plataforma (RF-24). Solo escucha eventos.
 *
 * <p>SCRUM-145: las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Notificaciones",
        allowedDependencies = {"compartido"})
package com.inversioneslr.marketplace.notificaciones;
