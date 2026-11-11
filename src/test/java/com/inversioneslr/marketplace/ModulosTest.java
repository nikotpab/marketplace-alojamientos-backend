package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

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

    /** Diagramas PlantUML de los módulos y sus dependencias en target/spring-modulith-docs. */
    @Test
    void documentaLosModulos() {
        new Documenter(MODULOS).writeDocumentation();

        assertThat(Path.of("target", "spring-modulith-docs")).isDirectoryContaining("glob:**.puml");
    }
}
