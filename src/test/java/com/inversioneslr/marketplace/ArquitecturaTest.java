package com.inversioneslr.marketplace;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;

/** Bloquean el merge si se rompen las fronteras (ADR-01, EC-28). */
@AnalyzeClasses(packages = ArquitecturaTest.BASE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    static final String BASE = "com.inversioneslr.marketplace";

    private static final List<String> MODULOS = List.of("usuarios", "publicaciones", "busqueda", "cotizacion", "reservas",
            "pagos", "cancelaciones", "notificaciones", "resenas", "administracion", "auditoria");

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

    @ArchTest
    static final ArchRule apiNoExponeInternos = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("..presentacion..", "..aplicacion..", "..infraestructura..");

    @ArchTest
    static final ArchRule stripeSoloEnSuAdaptador = noClasses()
            .that().resideOutsideOfPackage("..pagos.infraestructura..")
            .should().dependOnClassesThat().resideInAPackage("com.stripe..");

    @ArchTest
    static final ArchRule modulosSinCiclos = slices().matching(BASE + ".(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule otrosModulosSoloPorSuApi = noClasses().should(new ArchCondition<>("acceder a la parte interna de otro módulo") {
        @Override
        public void check(JavaClass clase, ConditionEvents eventos) {
            String origen = modulo(clase);
            for (Dependency dependencia : clase.getDirectDependenciesFromSelf()) {
                JavaClass destino = dependencia.getTargetClass();
                String moduloDestino = modulo(destino);
                boolean interna = moduloDestino != null && !moduloDestino.equals(origen)
                        && !destino.getPackageName().startsWith(BASE + "." + moduloDestino + ".api");
                eventos.add(new SimpleConditionEvent(dependencia, interna, dependencia.getDescription()));
            }
        }
    });

    private static String modulo(JavaClass clase) {
        return MODULOS.stream()
                .filter(modulo -> clase.getPackageName().startsWith(BASE + "." + modulo))
                .findFirst()
                .orElse(null);
    }
}
