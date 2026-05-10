package com.observation.portal.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class MvcLayerBoundaryTest {

    private static final String PORTAL_BASE_PACKAGE = "com.observation.portal";
    private static final Pattern CALCULATION_CLASS_NAME = Pattern.compile(".*(LifecycleState|InsightRule|EndpointPriority|P95).*");
    private static final JavaClasses PORTAL_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(PORTAL_BASE_PACKAGE);

    @Test
    void controllersDoNotAccessRepositoriesDirectly() {
        noClasses()
                .that().resideInAPackage("..portal.controller..")
                .should().dependOnClassesThat().resideInAnyPackage("..portal.repository..")
                .because("controllers must stay at the HTTP boundary and call services instead of repositories")
                .allowEmptyShould(true)
                .check(PORTAL_CLASSES);
    }

    @Test
    void repositoriesDoNotDependOnControllersOrDtos() {
        noClasses()
                .that().resideInAPackage("..portal.repository..")
                .should().dependOnClassesThat().resideInAnyPackage("..portal.controller..", "..portal.dto..")
                .because("repositories own persistence concerns and must not depend on HTTP boundary shapes")
                .allowEmptyShould(true)
                .check(PORTAL_CLASSES);
    }

    @Test
    void servicesDoNotDependOnControllersOrDtos() {
        noClasses()
                .that().resideInAPackage("..portal.service..")
                .should().dependOnClassesThat().resideInAnyPackage("..portal.controller..", "..portal.dto..")
                .because("services use internal commands, queries, and models instead of controller DTOs")
                .allowEmptyShould(true)
                .check(PORTAL_CLASSES);
    }

    @Test
    void calculationClassesStayInServiceOrModelPackages() {
        List<String> misplacedCalculationClasses = PORTAL_CLASSES.stream()
                .filter(javaClass -> CALCULATION_CLASS_NAME.matcher(javaClass.getSimpleName()).matches())
                .filter(MvcLayerBoundaryTest::isOutsideServiceAndModel)
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertThat(misplacedCalculationClasses)
                .as("state, insight rule, endpoint priority, and p95 calculation classes belong in service or model packages")
                .isEmpty();
    }

    private static boolean isOutsideServiceAndModel(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return !packageName.equals(PORTAL_BASE_PACKAGE + ".service")
                && !packageName.startsWith(PORTAL_BASE_PACKAGE + ".service.")
                && !packageName.equals(PORTAL_BASE_PACKAGE + ".model")
                && !packageName.startsWith(PORTAL_BASE_PACKAGE + ".model.");
    }
}
