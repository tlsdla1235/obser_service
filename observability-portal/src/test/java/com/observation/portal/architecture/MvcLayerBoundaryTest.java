package com.observation.portal.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.observation.portal.domain.history.controller.OperationalEventHistoryController;
import com.observation.portal.domain.history.service.OperationalEventHistoryProjector;
import com.observation.portal.domain.history.service.OperationalEventHistoryService;
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
    void accountFeatureUsesOnlyMvcPackageRoles() {
        List<String> accountClassesOutsideMvcRoles = PORTAL_CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName()
                        .startsWith("com.observation.portal.domain.account."))
                .filter(javaClass -> !javaClass.getSimpleName().equals("package-info"))
                .filter(MvcLayerBoundaryTest::isOutsideAccountMvcRolePackage)
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertThat(accountClassesOutsideMvcRoles)
                .as("Story 6.1 account code must stay in controller/dto/model/repository/service MVC packages")
                .isEmpty();
    }

    @Test
    void accountSchemaDoesNotOpenUnsupportedSignupSurfaces() throws IOException {
        try (Stream<Path> migrationFiles = Files.walk(Path.of("src/main/resources/db/migration"))) {
            List<String> forbiddenMigrationSnippets = migrationFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .flatMap(MvcLayerBoundaryTest::forbiddenAccountMigrationSnippets)
                    .sorted()
                    .toList();

            assertThat(forbiddenMigrationSnippets)
                    .as("Story 6.1 schema must not add password, magic link, non-GitHub provider, anonymous, or Redis surfaces")
                    .isEmpty();
        }
    }

    @Test
    void story58aNonGoalSurfacesAreNotPresent() {
        List<String> forbiddenClasses = PORTAL_CLASSES.stream()
                .map(JavaClass::getName)
                .filter(MvcLayerBoundaryTest::matchesStory58aForbiddenSurface)
                .filter(className -> !isEpic12IngestQueueSurface(className))
                .sorted()
                .toList();

        assertThat(forbiddenClasses)
                .as("Story 5.8-a/5.9-a must not add raw explorers, endpoint timeseries, event stores, or job infrastructure")
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

    @Test
    void story59bOperationalEventHistoryBoundaryDoesNotUseForbiddenLiveSources() {
        List<String> constructorParameterTypeNames = Stream.of(
                        OperationalEventHistoryController.class,
                        OperationalEventHistoryProjector.class,
                        OperationalEventHistoryService.class)
                .map(Class::getConstructors)
                .flatMap(Stream::of)
                .map(Constructor::getParameterTypes)
                .flatMap(Stream::of)
                .map(Class::getSimpleName)
                .toList();

        assertThat(constructorParameterTypeNames)
                .as("operational event history must project stored dashboard snapshots without live recalculation sources")
                .doesNotContain(
                        "MetricBucketRepository",
                        "StarterHeartbeatTelemetryRepository",
                        "DashboardReadModelService",
                        "LifecycleStateService",
                        "TriageSummaryService",
                        "EndpointPriorityService",
                        "InstanceEvidenceReadModelService");
    }

    @Test
    void story59aNonGoalPhysicalSurfacesAreNotPresent() throws IOException {
        List<String> forbiddenClasses = PORTAL_CLASSES.stream()
                .map(JavaClass::getName)
                .filter(MvcLayerBoundaryTest::matchesStory59aForbiddenPhysicalSurface)
                .sorted()
                .toList();

        assertThat(forbiddenClasses)
                .as("Story 5.9-a must not add operational event store, endpoint timeseries, or raw explorer classes")
                .isEmpty();

        try (Stream<Path> migrationFiles = Files.walk(Path.of("src/main/resources/db/migration"))) {
            List<String> forbiddenMigrationSnippets = migrationFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .flatMap(MvcLayerBoundaryTest::forbiddenStory59aMigrationSnippets)
                    .sorted()
                    .toList();

            assertThat(forbiddenMigrationSnippets)
                    .as("Story 5.9-a must not introduce event stores, endpoint timeseries, materialized views, outbox, or Redis")
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

    private static boolean isOutsideAccountMvcRolePackage(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return !hasPackageSegment(packageName, "controller")
                && !hasPackageSegment(packageName, "dto")
                && !hasPackageSegment(packageName, "model")
                && !hasPackageSegment(packageName, "repository")
                && !hasPackageSegment(packageName, "service");
    }

    private static boolean matchesStory58aForbiddenSurface(String className) {
        String normalized = className.toLowerCase();
        return normalized.contains("rawsnapshot")
                || normalized.contains("rawbucket")
                || normalized.contains("endpointtimeseries")
                || normalized.contains("operationaleventrepository")
                || normalized.contains("operationalevententity")
                || normalized.contains("springbatch")
                || normalized.contains(".batch.")
                || normalized.contains("outbox")
                || normalized.contains("redis")
                || normalized.contains("sqs")
                || normalized.contains("lambda")
                || normalized.contains("jobmetadata");
    }

    private static boolean isEpic12IngestQueueSurface(String className) {
        return className.startsWith("com.observation.portal.domain.ingest.queue.");
    }

    private static boolean matchesStory59aForbiddenPhysicalSurface(String className) {
        String normalized = className.toLowerCase();
        return normalized.contains("operationaleventrepository")
                || normalized.contains("operationalevententity")
                || normalized.contains("endpointtimeseries")
                || normalized.contains("rawsnapshot")
                || normalized.contains("rawbucket")
                || normalized.contains("rawexplorer");
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

    private static Stream<String> forbiddenStory59aMigrationSnippets(Path path) {
        try {
            String content = Files.readString(path).toLowerCase();
            return Stream.of("operational_events", "endpoint_timeseries", "materialized view", "outbox", "redis")
                    .filter(content::contains)
                    .map(snippet -> path + ":" + snippet);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read migration file: " + path, exception);
        }
    }

    private static Stream<String> forbiddenAccountMigrationSnippets(Path path) {
        try {
            String content = Files.readString(path).toLowerCase();
            return Stream.of(
                            "password_hash",
                            "password_reset",
                            "magic_link",
                            "email_verification",
                            "anonymous_user",
                            "google",
                            "kakao",
                            "naver",
                            "redis")
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
