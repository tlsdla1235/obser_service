package com.observation.portal.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
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
                .that().resideInAPackage("..portal.domain..controller..")
                .should().dependOnClassesThat().resideInAnyPackage("..portal.domain..repository..")
                .because("controllers must stay at the HTTP boundary and call services instead of repositories")
                .allowEmptyShould(true)
                .check(PORTAL_CLASSES);
    }

    @Test
    void repositoriesDoNotDependOnControllersOrDtos() {
        noClasses()
                .that().resideInAPackage("..portal.domain..repository..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..portal.domain..controller..",
                        "..portal.domain..dto..")
                .because("repositories own persistence concerns and must not depend on HTTP boundary shapes")
                .allowEmptyShould(true)
                .check(PORTAL_CLASSES);
    }

    @Test
    void servicesDoNotDependOnControllersOrDtos() {
        noClasses()
                .that().resideInAPackage("..portal.domain..service..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..portal.domain..controller..",
                        "..portal.domain..dto..")
                .because("services use internal commands, queries, and models instead of controller response DTOs")
                .allowEmptyShould(true)
                .check(PORTAL_CLASSES);
    }

    @Test
    void calculationClassesStayInServiceOrModelPackages() {
        List<String> misplacedCalculationClasses = PORTAL_CLASSES.stream()
                .filter(javaClass -> CALCULATION_CLASS_NAME.matcher(javaClass.getSimpleName()).matches())
                .filter(MvcLayerBoundaryTest::isOutsideServiceAndModelPackage)
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertThat(misplacedCalculationClasses)
                .as("state, insight rule, endpoint priority, and p95 calculation classes belong in service or model packages")
                .isEmpty();
    }

    @Test
    void hexagonalStylePackagesAreNotPresent() {
        List<String> hexagonalStylePackages = PORTAL_CLASSES.stream()
                .map(JavaClass::getPackageName)
                .distinct()
                .filter(MvcLayerBoundaryTest::containsHexagonalStyleSegment)
                .sorted()
                .toList();

        assertThat(hexagonalStylePackages)
                .as("feature-first MVC must not recreate port, adapter, or application package boundaries")
                .isEmpty();
    }

    private static boolean isOutsideServiceAndModelPackage(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return !hasPackageSegment(packageName, "service") && !hasPackageSegment(packageName, "model");
    }

    private static boolean containsHexagonalStyleSegment(String packageName) {
        Set<String> forbiddenSegments = Set.of("port", "adapter", "application");
        for (String segment : packageName.split("\\.")) {
            if (forbiddenSegments.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPackageSegment(String packageName, String expectedSegment) {
        for (String segment : packageName.split("\\.")) {
            if (segment.equals(expectedSegment)) {
                return true;
            }
        }
        return false;
    }
}
