/**
 * Cobros y devoluciones con la pasarela.
 *
 * <p>Las dependencias permitidas son las reales, derivadas con {@code ApplicationModules}. Una dependencia
 * nueva hacia otro módulo hace fallar {@code ModulosTest} hasta que se agregue aquí a propósito.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Pagos",
        allowedDependencies = {"compartido"})
package com.inversioneslr.marketplace.pagos;
