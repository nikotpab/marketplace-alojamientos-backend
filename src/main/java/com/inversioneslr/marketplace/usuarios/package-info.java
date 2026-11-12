/**
 * Registro, inicio de sesión, perfil, correos y cuentas (RF-01 a RF-05).
 *
 * <p>SCRUM-145: las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Usuarios",
        allowedDependencies = {"compartido"})
package com.inversioneslr.marketplace.usuarios;
