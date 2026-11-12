/**
 * Registro de solo inserción de todos los eventos de dominio (ASR-07, ADR-06).
 *
 * <p>SCRUM-145: las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Auditoría",
        allowedDependencies = {"compartido", "usuarios"})
package com.inversioneslr.marketplace.auditoria;
