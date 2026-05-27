package com.observation.portal.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailService;
import com.observation.portal.domain.snapshot.service.DashboardSnapshotMarkerService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
    void stateFeatureClassesStayInModelOrServicePackages() {
        List<String> misplacedStateFeatureClasses = PORTAL_CLASSES.stream()
                .filter(MvcLayerBoundaryTest::isStateFeaturePackage)
                .filter(javaClass -> !javaClass.getSimpleName().equals("package-info"))
                .filter(MvcLayerBoundaryTest::isOutsideServiceAndModelPackage)
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertThat(misplacedStateFeatureClasses)
                .as("lifecycle state calculation and result classes must stay inside domain.state.model or domain.state.service")
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

    @Test
    void story58aNonGoalSurfacesAreNotPresent() {
        List<String> forbiddenClasses = PORTAL_CLASSES.stream()
                .map(JavaClass::getName)
                .filter(MvcLayerBoundaryTest::matchesStory58aForbiddenSurface)
                .sorted()
                .toList();

        assertThat(forbiddenClasses)
                .as("Story 5.8-a must not add raw explorers, endpoint timeseries, operational events, or job infrastructure")
                .isEmpty();
    }

    @Test
    void story58bSnapshotDetailAndMarkerServicesDoNotUseCurrentRecalculationDependencies() {
        List<String> constructorParameterTypeNames = Stream.of(
                        DashboardSnapshotDetailService.class,
                        DashboardSnapshotMarkerService.class)
                .map(Class::getConstructors)
                .flatMap(Stream::of)
                .map(Constructor::getParameterTypes)
                .flatMap(Stream::of)
                .map(Class::getSimpleName)
                .toList();

        assertThat(constructorParameterTypeNames)
                .as("snapshot detail/marker services must project stored snapshots without current recalculation sources")
                .doesNotContain(
                        "MetricBucketRepository",
                        "StarterHeartbeatTelemetryRepository",
                        "LifecycleStateService",
                        "TriageSummaryService",
                        "EndpointPriorityService",
                        "DashboardReadModelService",
                        "InstanceEvidenceReadModelService");
    }

    @Test
    void story58bNonGoalDatabaseSurfacesAreNotPresent() throws IOException {
        try (Stream<Path> migrationFiles = Files.walk(Path.of("src/main/resources/db/migration"))) {
            List<String> forbiddenMigrationSnippets = migrationFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .flatMap(MvcLayerBoundaryTest::forbiddenMigrationSnippets)
                    .sorted()
                    .toList();

            assertThat(forbiddenMigrationSnippets)
                    .as("Story 5.8-b must not introduce operational_events or endpoint timeseries tables")
                    .isEmpty();
        }
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

    private static boolean isStateFeaturePackage(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return packageName.equals("com.observation.portal.domain.state")
                || packageName.startsWith("com.observation.portal.domain.state.");
    }

    private static boolean matchesStory58aForbiddenSurface(String className) {
        String normalized = className.toLowerCase();
        return normalized.contains("rawsnapshot")
                || normalized.contains("rawbucket")
                || normalized.contains("endpointtimeseries")
                || normalized.contains("operationalevent")
                || normalized.contains("springbatch")
                || normalized.contains(".batch.")
                || normalized.contains("outbox")
                || normalized.contains("redis")
                || normalized.contains("sqs")
                || normalized.contains("lambda")
                || normalized.contains("jobmetadata");
    }

    private static Stream<String> forbiddenMigrationSnippets(Path path) {
        try {
            String content = Files.readString(path).toLowerCase();
            return Stream.of("operational_events", "endpoint_timeseries")
                    .filter(content::contains)
                    .map(snippet -> path + ":" + snippet);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read migration file: " + path, exception);
        }
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
