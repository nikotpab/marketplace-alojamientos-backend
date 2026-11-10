package com.inversioneslr.marketplace;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Capas dentro de cada módulo (ADR-01). Las fronteras entre módulos las verifica {@link ModulosTest}.
 * Bloquean el merge si se rompen (EC-28).
 */
@AnalyzeClasses(packages = "com.inversioneslr.marketplace", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    @ArchTest
    static final ArchRule dominioNoDependeDePresentacionNiInfraestructura = noClasses()
            .that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat().resideInAnyPackage("..presentacion..", "..aplicacion..", "..infraestructura..",
                    "org.springframework..", "jakarta..", "com.stripe..", "org.postgresql..");

    @ArchTest
    static final ArchRule aplicacionNoDependeDePresentacionNiInfraestructura = noClasses()
            .that().resideInAPackage("..aplicacion..")
            .should().dependOnClassesThat().resideInAnyPackage("..presentacion..", "..infraestructura..");

    @ArchTest
    static final ArchRule presentacionNoDependeDeInfraestructura = noClasses()
            .that().resideInAPackage("..presentacion..")
            .should().dependOnClassesThat().resideInAPackage("..infraestructura..");

    /** La raíz del módulo es su interfaz pública: no debe exponer tipos de sus capas internas. */
    @ArchTest
    static final ArchRule interfazPublicaNoExponeInternos = noClasses()
            .that().resideInAPackage("com.inversioneslr.marketplace.*")
            .should().dependOnClassesThat().resideInAnyPackage("..presentacion..", "..aplicacion..", "..infraestructura..");

    @ArchTest
    static final ArchRule stripeSoloEnSuAdaptador = noClasses()
            .that().resideOutsideOfPackage("..pagos.infraestructura..")
            .should().dependOnClassesThat().resideInAPackage("com.stripe..");
}
