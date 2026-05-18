package com.observation.starter.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterObservationArchitectureTest {

    private static final String STARTER_BASE_PACKAGE = "com.observation.starter";
    private static final JavaClasses STARTER_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(STARTER_BASE_PACKAGE);

    @Test
    void observationBindingDoesNotDependOnRequestPathIngestInfrastructure() {
        noClasses()
                .that().resideInAPackage("..starter.spring.observation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..starter.client..",
                        "..starter.queue..",
                        "java.net..",
                        "java.net.http..",
                        "org.springframework.web.client..",
                        "org.springframework.web.reactive.function.client..",
                        "com.fasterxml.jackson.databind..")
                .because("Story 2.1 binding records local samples and must not perform network, queue, or serialization work")
                .allowEmptyShould(true)
                .check(STARTER_CLASSES);
    }

    @Test
    void starterModelDoesNotStoreServletOrSpringWebRequestTypes() {
        noClasses()
                .that().resideInAPackage("..starter.model..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..",
                        "javax.servlet..",
                        "org.springframework.http.server..",
                        "org.springframework.web..")
                .because("starter model keeps internal observation input, not servlet request or response objects")
                .allowEmptyShould(true)
                .check(STARTER_CLASSES);
    }

    @Test
    void bucketRollupServiceDoesNotDependOnTransportQueueOrEnvelopeSerialization() {
        noClasses()
                .that().haveNameMatching(".*MetricBucketRollupService.*")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..starter.client..",
                        "..starter.queue..",
                        "java.net..",
                        "java.net.http..",
                        "org.springframework.web.client..",
                        "org.springframework.web.reactive.function.client..",
                        "com.fasterxml.jackson.databind..")
                .because("Story 2.3 rollup service must not perform network, HTTP client, queue, or envelope work")
                .allowEmptyShould(true)
                .check(STARTER_CLASSES);
    }

    @Test
    void requestPathDoesNotCallPortalHttpClientImplementationDirectly() {
        noClasses()
                .that().resideInAPackage("..starter.spring.observation..")
                .or().haveSimpleName("StarterMetricIngestService")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..starter.client..",
                        "..starter.client.http..",
                        "java.net..",
                        "java.net.http..",
                        "org.springframework.web.client..",
                        "org.springframework.web.reactive.function.client..",
                        "com.fasterxml.jackson.databind..")
                .because("Story 2.4 request path and tick drain only record locally and enqueue due buckets")
                .allowEmptyShould(true)
                .check(STARTER_CLASSES);
    }

    @Test
    void ingestDrainBoundaryDoesNotDependOnEnvelopeOrIdempotencyBuilders() {
        noClasses()
                .that().haveSimpleName("StarterMetricIngestService")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..starter.envelope..",
                        "..starter.serialization..",
                        "..starter.idempotency..")
                .because("Story 2.4 drain/tick boundary stops at queue offer; final envelope and idempotency are Story 2.5")
                .allowEmptyShould(true)
                .check(STARTER_CLASSES);

        noClasses()
                .that().haveSimpleName("StarterMetricIngestService")
                .should().dependOnClassesThat().haveNameMatching(".*(IngestEnvelope|Idempotency).*")
                .because("Story 2.4 must not call final envelope serialization or idempotency key generation")
                .allowEmptyShould(true)
                .check(STARTER_CLASSES);
    }

    @Test
    void scheduledDrainTriggerDoesNotDependOnPortalTransportSerializationOrIdempotency() {
        noClasses()
                .that().haveSimpleName("StarterMetricDrainScheduler")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..starter.client..",
                        "..starter.client.http..",
                        "java.net..",
                        "java.net.http..",
                        "org.springframework.web.client..",
                        "org.springframework.web.reactive.function.client..",
                        "com.fasterxml.jackson.databind..",
                        "..starter.envelope..",
                        "..starter.serialization..",
                        "..starter.idempotency..")
                .because("scheduled idle drain must only tick the local rollup drain boundary")
                .allowEmptyShould(true)
                .check(STARTER_CLASSES);
    }

    @Test
    void portalClientBoundaryIsOnlyUsedByBackgroundFlushWorker() {
        noClasses()
                .that().doNotHaveSimpleName("MetricBucketFlushWorker")
                .and().doNotHaveSimpleName("MetricDrainAutoConfiguration")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.observation.starter.client.PortalMetricBucketClient")
                .because("portal client invocation stays behind the worker; auto-configuration may wire the bean")
                .allowEmptyShould(true)
                .check(STARTER_CLASSES);
    }

    @Test
    void hexagonalStylePackagesAreNotPresentInStarter() {
        List<String> forbiddenPackages = STARTER_CLASSES.stream()
                .map(JavaClass::getPackageName)
                .distinct()
                .filter(StarterObservationArchitectureTest::containsForbiddenSegment)
                .sorted()
                .toList();

        assertTrue(forbiddenPackages.isEmpty(),
                () -> "starter must not create application, port, or adapter packages: " + forbiddenPackages);
    }

    private static boolean containsForbiddenSegment(String packageName) {
        Set<String> forbiddenSegments = Set.of("application", "port", "adapter");
        for (String segment : packageName.split("\\.")) {
            if (forbiddenSegments.contains(segment)) {
                return true;
            }
        }
        return false;
    }
}
