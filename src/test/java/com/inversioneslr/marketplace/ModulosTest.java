package com.inversioneslr.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fronteras entre módulos con Spring Modulith (ADR-01, EC-28). Cada paquete directo de
 * {@code marketplace} es un módulo; lo público es su paquete raíz y sus subpaquetes son internos.
 */
class ModulosTest {

    private static final ApplicationModules MODULOS = ApplicationModules.of(MarketplaceApiApplication.class);

    @Test
    void losModulosNoTienenCiclosNiUsanLaParteInternaDeOtro() {
        MODULOS.verify();
    }
}
