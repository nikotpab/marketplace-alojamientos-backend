/**
 * Moderación, configuración, dashboard, reportes y consulta de la auditoría (RF-27 a RF-35). Su dashboard y sus reportes son un modelo de lectura.
 *
 * <p>SCRUM-145: las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Administración",
        allowedDependencies = {"auditoria", "compartido", "cotizacion", "publicaciones", "resenas", "reservas", "usuarios"})
package com.inversioneslr.marketplace.administracion;
