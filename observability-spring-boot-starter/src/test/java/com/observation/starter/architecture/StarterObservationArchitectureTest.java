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
