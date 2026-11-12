/**
 * Núcleo compartido: eventos, identificadores, errores, seguridad y logs. No depende de ningún otro módulo.
 *
 * <p>SCRUM-145: las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Compartido",
        allowedDependencies = {})
package com.inversioneslr.marketplace.compartido;
