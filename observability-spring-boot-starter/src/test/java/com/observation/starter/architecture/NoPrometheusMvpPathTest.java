package com.observation.starter.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.observation.starter.model.ingest.IngestEnvelope;
import com.observation.starter.model.ingest.IngestEnvelopeCandidate;
import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import com.observation.starter.model.metric.AppMetricRollup;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.EndpointKey;
import com.observation.starter.model.metric.EndpointMetricRollup;
import com.observation.starter.model.metric.HistogramBucket;
import com.observation.starter.model.metric.JvmMetricSample;
import com.observation.starter.model.metric.LocalPercentileRollup;
import com.observation.starter.model.metric.LowCardinalityHttpServerObservation;
import com.observation.starter.model.metric.LowCardinalityTagKey;
import com.observation.starter.model.route.NormalizedRoute;
import com.observation.starter.model.time.MetricBucketInterval;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 2.6 negative guard: active starter source/build/resource 범위에서 MVP 역행 경로를 차단한다.
 *
 * <p>planning/archive 문서의 과거 Prometheus 설명은 검사하지 않고, 실제 starter 산출물에 포함되는
 * 경로만 검사한다.</p>
 */
class NoPrometheusMvpPathTest {

    private static final Path STARTER_ROOT = resolveStarterRoot();
    private static final Path MAIN_SOURCE_ROOT = STARTER_ROOT.resolve("src/main");
    private static final Path MAIN_JAVA = MAIN_SOURCE_ROOT.resolve("java");
    private static final Path MAIN_RESOURCES = STARTER_ROOT.resolve("src/main/resources");
    private static final Path BUILD_FILE = STARTER_ROOT.resolve("build.gradle");
    private static final Path STARTER_PACKAGE_ROOT =
            MAIN_JAVA.resolve("com/observation/starter");
    private static final List<String> MAIN_SOURCE_DIRECTORIES = List.of(
            "java",
            "kotlin",
            "groovy",
            "scala");
    private static final List<String> MAIN_SOURCE_EXTENSIONS = List.of(
            ".java",
            ".kt",
            ".kts",
            ".groovy",
            ".scala");
    private static final List<String> FORBIDDEN_ENDPOINT_PATH_FRAGMENTS = List.of(
            "/actuator/prometheus",
            "/api/v1/query",
            "/metrics/query",
            "/metrics",
            "/prometheus",
            "/query",
            "/scrape",
            "/export");
    private static final List<String> ALLOWED_STARTER_LOCAL_PERCENTILE_PATHS = List.of(
            "src/main/java/com/observation/starter/model/ingest/IngestEnvelope.java",
            "src/main/java/com/observation/starter/model/metric/AppMetricRollup.java",
            "src/main/java/com/observation/starter/model/metric/ClosedMetricBucket.java",
            "src/main/java/com/observation/starter/model/metric/LocalPercentileRollup.java",
            "src/main/java/com/observation/starter/service/IngestEnvelopeBuilderService.java",
            "src/main/java/com/observation/starter/service/MetricBucketRollupService.java");
    private static final List<String> GUARDED_CLASSPATH_PROPERTIES = List.of(
            "starter.guard.mainCompileClasspath",
            "starter.guard.mainRuntimeClasspath",
            "starter.guard.testCompileClasspath",
            "starter.guard.testRuntimeClasspath");
    private static final List<ForbiddenClasspathSignal> FORBIDDEN_CLASSPATH_SIGNALS = List.of(
            new ForbiddenClasspathSignal("micrometer Prometheus registry", ".*micrometer-registry-prometheus.*"),
            new ForbiddenClasspathSignal("Prometheus dependency", ".*prometheus.*"),
            new ForbiddenClasspathSignal("PromQL dependency", ".*promql.*"),
            new ForbiddenClasspathSignal("simpleclient dependency", ".*simpleclient.*"),
            new ForbiddenClasspathSignal("actuator dependency", ".*spring-boot-(starter-)?actuator.*"),
            new ForbiddenClasspathSignal("Spring Boot web starter", ".*spring-boot-starter-web(flux)?.*"),
            new ForbiddenClasspathSignal("Spring Web dependency", ".*spring-web(mvc|flux)?.*"),
            new ForbiddenClasspathSignal("query dependency", ".*query.*"));
    private static final List<String> FORBIDDEN_CLASSPATH_CLASSES = List.of(
            "io.micrometer.prometheusmetrics.PrometheusMeterRegistry",
            "io.micrometer.prometheus.PrometheusMeterRegistry",
            "io.prometheus.client.CollectorRegistry",
            "io.prometheus.metrics.model.registry.PrometheusRegistry",
            "org.springframework.boot.actuate.autoconfigure.metrics.MetricsEndpointAutoConfiguration",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.web.reactive.function.client.WebClient");
    private static final Map<Class<?>, List<AllowedRecordComponent>> GUARDED_MODEL_COMPONENT_ALLOWLIST =
            Map.ofEntries(
                    Map.entry(IngestEnvelopeCandidate.class, List.of(
                            component("payload", IngestEnvelope.class),
                            component("idempotencyKey", String.class))),
                    Map.entry(IngestEnvelope.class, List.of(
                            component("schemaVersion", String.class),
                            component("application", IngestEnvelope.Application.class),
                            component("bucket", IngestEnvelope.Bucket.class),
                            component("summary", IngestEnvelope.Summary.class),
                            listComponent("endpoints", IngestEnvelope.Endpoint.class))),
                    Map.entry(IngestEnvelope.Application.class, List.of(
                            component("name", String.class),
                            component("environment", String.class),
                            component("instance", String.class))),
                    Map.entry(IngestEnvelope.Bucket.class, List.of(
                            component("startUtc", String.class),
                            component("endUtc", String.class),
                            component("durationSeconds", int.class))),
                    Map.entry(IngestEnvelope.Summary.class, List.of(
                            component("requestCount", long.class),
                            component("errorCount", long.class),
                            listComponent("httpServerDurationBuckets", IngestEnvelope.DurationBucket.class),
                            component("jvm", IngestEnvelope.Jvm.class),
                            component("datasource", IngestEnvelope.Datasource.class),
                            component("localPercentiles", IngestEnvelope.LocalPercentiles.class))),
                    Map.entry(IngestEnvelope.LocalPercentiles.class, List.of(
                            component("scope", String.class),
                            component("source", String.class),
                            component("bucketStartUtc", String.class),
                            component("bucketEndUtc", String.class),
                            component("requestCount", long.class),
                            component("p95Ms", long.class),
                            component("p99Ms", long.class),
                            component("mergeable", boolean.class))),
                    Map.entry(IngestEnvelope.Endpoint.class, List.of(
                            component("method", String.class),
                            component("route", String.class),
                            component("requestCount", long.class),
                            component("errorCount", long.class),
                            listComponent("durationBuckets", IngestEnvelope.DurationBucket.class))),
                    Map.entry(IngestEnvelope.DurationBucket.class, List.of(
                            component("leMs", long.class),
                            component("count", long.class))),
                    Map.entry(IngestEnvelope.Jvm.class, List.of(
                            component("cpuUsage", double.class),
                            component("heapUsedRatio", double.class))),
                    Map.entry(IngestEnvelope.Datasource.class, List.of(
                            component("poolUsageRatio", double.class))),
                    Map.entry(IngestEnvelopeIdentity.class, List.of(
                            component("projectId", String.class),
                            component("applicationName", String.class),
                            component("environment", String.class),
                            component("instance", String.class))),
                    Map.entry(LowCardinalityHttpServerObservation.class, List.of(
                            component("observedAt", java.time.Instant.class),
                            component("method", String.class),
                            component("statusCode", Integer.class),
                            component("error", boolean.class),
                            component("errorType", String.class),
                            component("duration", java.time.Duration.class),
                            component("normalizedRoute", NormalizedRoute.class))),
                    Map.entry(EndpointKey.class, List.of(
                            component("method", String.class),
                            component("normalizedRoute", NormalizedRoute.class))),
                    Map.entry(AppMetricRollup.class, List.of(
                            component("requestCount", long.class),
                            component("errorCount", long.class),
                            listComponent("httpServerDurationBuckets", HistogramBucket.class),
                            optionalComponent("jvm", JvmMetricSample.class),
                            optionalComponent("datasource", DatasourcePoolMetricSample.class),
                            optionalComponent("localPercentiles", LocalPercentileRollup.class))),
                    Map.entry(ClosedMetricBucket.class, List.of(
                            component("interval", MetricBucketInterval.class),
                            component("appSummary", AppMetricRollup.class),
                            listComponent("endpointRollups", EndpointMetricRollup.class))),
                    Map.entry(EndpointMetricRollup.class, List.of(
                            component("endpointKey", EndpointKey.class),
                            component("requestCount", long.class),
                            component("errorCount", long.class),
                            listComponent("durationBuckets", HistogramBucket.class))),
                    Map.entry(HistogramBucket.class, List.of(
                            component("leMs", long.class),
                            component("count", long.class))),
                    Map.entry(JvmMetricSample.class, List.of(
                            component("observedAt", java.time.Instant.class),
                            component("cpuUsageRatio", double.class),
                            component("heapUsedRatio", double.class))),
                    Map.entry(LocalPercentileRollup.class, List.of(
                            component("requestCount", long.class),
                            component("p95Ms", long.class),
                            component("p99Ms", long.class))),
                    Map.entry(DatasourcePoolMetricSample.class, List.of(
                            component("observedAt", java.time.Instant.class),
                            component("poolUsageRatio", double.class))),
                    Map.entry(NormalizedRoute.class, List.of(
                            component("value", String.class))));

    @Test
    void guardScopeIsLimitedToActiveStarterBuildSourceAndResources() throws IOException {
        List<Path> scopedFiles = activeGuardFiles();

        assertFalse(scopedFiles.isEmpty());
        assertTrue(scopedFiles.stream().allMatch(path -> path.startsWith(STARTER_ROOT)));
        assertTrue(scopedFiles.stream().noneMatch(path -> containsPathSegment(path, "planning-artifacts")
                || containsPathSegment(path, "archive")
                || isTestSourcePath(path)),
                () -> "negative guard must ignore planning/archive/test artifacts: " + relativePaths(scopedFiles));
    }

    @Test
    void guardScopeSelfTestDetectsSrcTestPathSequence() {
        Path testSource = STARTER_ROOT.resolve("src/test/java/com/observation/starter/architecture/Fixture.java");
        Path mainSource = STARTER_ROOT.resolve("src/main/java/com/observation/starter/Fixture.java");

        assertTrue(isTestSourcePath(testSource));
        assertFalse(isTestSourcePath(mainSource));
    }

    @Test
    void mainSourceScannerRecognizesJvmLanguageSourceFilesOnly() {
        assertTrue(isSupportedMainSourceFile(STARTER_ROOT.resolve("src/main/java/Foo.java")));
        assertTrue(isSupportedMainSourceFile(STARTER_ROOT.resolve("src/main/kotlin/Foo.kt")));
        assertTrue(isSupportedMainSourceFile(STARTER_ROOT.resolve("src/main/groovy/Foo.groovy")));
        assertFalse(isSupportedMainSourceFile(STARTER_ROOT.resolve("src/main/resources/application.yml")));
        assertFalse(isSupportedMainSourceFile(STARTER_ROOT.resolve("src/test/java/Foo.java")));
    }

    @Test
    void starterDoesNotContainApplicationPortOrAdapterPackages() throws IOException {
        List<Path> forbiddenPackagePaths = mainSourceFiles().stream()
                .filter(path -> containsAnyPathSegment(path, List.of("application", "port", "adapter")))
                .toList();
        List<Path> forbiddenPackageDirectories = List.of("application", "port", "adapter").stream()
                .map(STARTER_PACKAGE_ROOT::resolve)
                .filter(Files::exists)
                .toList();

        assertTrue(forbiddenPackagePaths.isEmpty(),
                () -> "starter source must not create application/port/adapter packages: "
                        + relativePaths(forbiddenPackagePaths));
        assertTrue(forbiddenPackageDirectories.isEmpty(),
                () -> "starter source must not create application/port/adapter directories: "
                        + relativePaths(forbiddenPackageDirectories));
    }

    @Test
    void starterDoesNotContainMvcOrMetricsScrapeControllers() throws IOException {
        List<Path> controllerPaths = mainSourceFiles().stream()
                .filter(path -> containsPathSegment(path, "controller")
                        || containsPathSegment(path, "endpoint")
                        || sourceFileBaseName(path).endsWith("Controller"))
                .toList();
        List<Path> annotatedControllerPaths = mainSourceFiles().stream()
                .filter(NoPrometheusMvpPathTest::containsControllerAnnotationOrImport)
                .toList();

        assertTrue(controllerPaths.isEmpty(),
                () -> "starter must remain a host app library without MVC/scrape controllers: "
                        + relativePaths(controllerPaths));
        assertTrue(annotatedControllerPaths.isEmpty(),
                () -> "starter must not import or declare Spring MVC controller annotations: "
                        + relativePaths(annotatedControllerPaths));
    }

    @Test
    void starterBuildAndResolvedClasspathsDoNotDeclarePrometheusScrapeExportOrQueryDependencies()
            throws IOException {
        assertGuardClasspathPropertiesArePresent();

        String buildText = uncommentedLines(Files.readString(BUILD_FILE)).toLowerCase(Locale.ROOT);
        List<String> forbiddenDependencies = List.of(
                "micrometer-registry-prometheus",
                "prometheus",
                "promql",
                "simpleclient",
                "spring-boot-starter-actuator",
                "spring-boot-starter-web",
                "spring-boot-starter-webflux",
                "spring-web",
                "spring-webflux",
                "spring-webmvc");
        List<String> matches = forbiddenDependencies.stream()
                .filter(buildText::contains)
                .toList();
        List<String> classpathMatches = forbiddenResolvedClasspathEntries();
        List<String> forbiddenLoadedClasses = FORBIDDEN_CLASSPATH_CLASSES.stream()
                .filter(NoPrometheusMvpPathTest::isClassPresent)
                .toList();

        assertTrue(matches.isEmpty(),
                () -> "starter build must not add Prometheus scrape/export/query or MVC web dependencies: "
                        + matches);
        assertTrue(classpathMatches.isEmpty(),
                () -> "starter resolved compile/runtime/test classpaths must not include Prometheus, actuator, web, or query dependencies: "
                        + classpathMatches);
        assertTrue(forbiddenLoadedClasses.isEmpty(),
                () -> "starter test runtime classloader must not expose forbidden Prometheus, actuator, or web classes: "
                        + forbiddenLoadedClasses);
    }

    @Test
    void starterResourcesDoNotDeclarePrometheusScrapeTargetsPromqlProfilesOrQueryUi() throws IOException {
        List<Path> resourceOffenders = regularFiles(MAIN_RESOURCES).stream()
                .filter(NoPrometheusMvpPathTest::containsForbiddenResourceMvpSignal)
                .toList();

        assertTrue(resourceOffenders.isEmpty(),
                () -> "starter resources must not include scrape config, Prometheus targets, PromQL profiles, or query UI assets: "
                        + relativePaths(resourceOffenders));
    }

    @Test
    void starterCodeDoesNotExposePrometheusScrapeExportQueryUiOrBuilderPaths() throws IOException {
        List<Path> nameOffenders = mainSourceFiles().stream()
                .filter(NoPrometheusMvpPathTest::hasForbiddenMvpPathName)
                .toList();
        List<Path> codeOffenders = mainSourceFiles().stream()
                .filter(NoPrometheusMvpPathTest::containsForbiddenMvpPathCode)
                .toList();

        assertTrue(nameOffenders.isEmpty(),
                () -> "starter code must not add Prometheus, scrape, export, query UI, read model, or priority paths: "
                        + relativePaths(nameOffenders));
        assertTrue(codeOffenders.isEmpty(),
                () -> "starter code must not expose Prometheus, scrape, export, query UI, read model, or priority symbols: "
                        + relativePaths(codeOffenders));
    }

    @Test
    void envelopeCandidateModelsMatchExplicitRecordComponentAllowlist() {
        List<Class<?>> envelopeModelClasses = List.of(
                IngestEnvelopeCandidate.class,
                IngestEnvelope.class,
                IngestEnvelope.Application.class,
                IngestEnvelope.Bucket.class,
                IngestEnvelope.Summary.class,
                IngestEnvelope.LocalPercentiles.class,
                IngestEnvelope.Endpoint.class,
                IngestEnvelope.DurationBucket.class,
                IngestEnvelope.Jvm.class,
                IngestEnvelope.Datasource.class,
                IngestEnvelopeIdentity.class);

        for (Class<?> modelClass : envelopeModelClasses) {
            assertRecordComponentsMatchAllowlist(modelClass);
        }
    }

    @Test
    void guardedProducerModelsMatchExplicitRecordComponentAllowlist() {
        List<Class<?>> producerModelClasses = List.of(
                LowCardinalityHttpServerObservation.class,
                EndpointKey.class,
                AppMetricRollup.class,
                LocalPercentileRollup.class,
                ClosedMetricBucket.class,
                EndpointMetricRollup.class,
                HistogramBucket.class,
                JvmMetricSample.class,
                DatasourcePoolMetricSample.class,
                NormalizedRoute.class);

        for (Class<?> modelClass : producerModelClasses) {
            assertRecordComponentsMatchAllowlist(modelClass);
        }
    }

    @Test
    void lowCardinalityTagAllowlistDoesNotOpenCustomIdentityDimensions() {
        List<String> allowedExternalKeys = Arrays.stream(LowCardinalityTagKey.values())
                .map(LowCardinalityTagKey::externalKey)
                .toList();

        assertEquals(
                List.of("application", "environment", "instance", "method", "normalizedRoute"),
                allowedExternalKeys);
        assertFalse(LowCardinalityTagKey.isAllowedExternalKey("tenantId"));
        assertFalse(LowCardinalityTagKey.isAllowedExternalKey("userId"));
        assertFalse(LowCardinalityTagKey.isAllowedExternalKey("sessionId"));
        assertFalse(LowCardinalityTagKey.isAllowedExternalKey("traceId"));
        assertFalse(LowCardinalityTagKey.isAllowedExternalKey("customLabel"));
        assertFalse(LowCardinalityTagKey.isAllowedExternalKey("metricName"));
    }

    @Test
    void modelCarrierGuardRejectsUnapprovedCarrierShapesEvenWhenNamesLookNeutral() {
        assertCarrierFixtureRejected(ObjectPayloadFixture.class, component("payload", IngestEnvelope.class));
        assertCarrierFixtureRejected(PropertiesPayloadFixture.class, component("payload", IngestEnvelope.class));
        assertCarrierFixtureRejected(JsonNodePayloadFixture.class, component("payload", IngestEnvelope.class));
        assertCarrierFixtureRejected(ListStringValuesFixture.class, listComponent("values", HistogramBucket.class));
        assertCarrierFixtureRejected(MapPayloadFixture.class, component("payload", IngestEnvelope.class));
        assertCarrierFixtureRejected(ArrayPayloadFixture.class, component("payload", IngestEnvelope.class));
        assertCarrierFixtureRejected(RawGenericCarrierFixture.class, listComponent("payload", HistogramBucket.class));
        assertCarrierFixtureRejected(NeutralNameCarrierFixture.class, component("value", String.class));
    }

    @Test
    void javaSourceScannerDetectsForbiddenPrometheusPathsInsideUrlLiterals() {
        String source = "class Fixture {\n"
                + "  String metricsQuery = \"https://example.test/metrics/query\";\n"
                + "  String apiQuery = \"http://prometheus.local/api/v1/query\";\n"
                + "  String blockMarkerPath = \"literal /* /api/v1/query */ still code string\";\n"
                + "  String textBlock = \"\"\"\n"
                + "      https://example.test/metrics/query\n"
                + "      \"\"\";\n"
                + "}\n";

        assertTrue(containsForbiddenMvpPathCodeText(source));
    }

    @Test
    void jvmSourceScannerDetectsForbiddenPathsInsideJavaKotlinAndGroovyStringLiterals() {
        String javaSource = "class Fixture { String value = \"https://example.test/metrics/query\"; }\n";
        String kotlinSource = "class Fixture { val value = \"\"\"https://example.test/api/v1/query\"\"\" }\n";
        String groovySource = "class Fixture { def value = 'https://example.test/prometheus' }\n";

        assertTrue(containsForbiddenMvpPathCodeText(javaSource));
        assertTrue(containsForbiddenMvpPathCodeText(kotlinSource));
        assertTrue(containsForbiddenMvpPathCodeText(groovySource));
    }

    @Test
    void sourceScannerIgnoresForbiddenPrometheusPathsAndControllerAnnotationsInsideRealComments() {
        String source = "class Fixture {\n"
                + "  // @RestController @Endpoint /actuator/prometheus\n"
                + "  // https://example.test/metrics/query\n"
                + "  /* @GetMapping(\"/metrics\") http://prometheus.local/api/v1/query */\n"
                + "  String harmless = \"https://example.test/no-query\";\n"
                + "  String markerText = \"literal // and /* markers */ are not comments\";\n"
                + "}\n";

        assertFalse(containsForbiddenMvpPathCodeText(source));
        assertFalse(containsControllerAnnotationOrImportText(source));
    }

    private static Path resolveStarterRoot() {
        Path workingDirectory = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(workingDirectory.resolve("src/main/java/com/observation/starter"))) {
            return workingDirectory;
        }

        Path moduleDirectory = workingDirectory.resolve("observability-spring-boot-starter");
        if (Files.exists(moduleDirectory.resolve("src/main/java/com/observation/starter"))) {
            return moduleDirectory;
        }

        throw new IllegalStateException("Cannot locate observability-spring-boot-starter from " + workingDirectory);
    }

    private static List<Path> activeGuardFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        files.add(BUILD_FILE);
        files.addAll(mainSourceFiles());
        files.addAll(regularFiles(MAIN_RESOURCES));
        return files.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .sorted()
                .toList();
    }

    private static List<Path> mainSourceFiles() throws IOException {
        return regularFiles(MAIN_SOURCE_ROOT).stream()
                .filter(NoPrometheusMvpPathTest::isSupportedMainSourceFile)
                .toList();
    }

    private static List<Path> regularFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .sorted()
                    .toList();
        }
    }

    private static boolean containsControllerAnnotationOrImport(Path sourceFile) {
        return containsControllerAnnotationOrImportText(readUnchecked(sourceFile));
    }

    private static boolean containsControllerAnnotationOrImportText(String rawText) {
        String text = stripComments(rawText);
        return text.contains("@Controller")
                || text.contains("@RestController")
                || text.contains("@RequestMapping")
                || text.contains("@GetMapping")
                || text.contains("@PostMapping")
                || text.contains("@PutMapping")
                || text.contains("@DeleteMapping")
                || text.contains("@PatchMapping")
                || text.contains("@Endpoint")
                || text.contains("@ReadOperation")
                || text.contains("RouterFunction")
                || text.contains("org.springframework.boot.actuate.endpoint.annotation")
                || text.contains("org.springframework.stereotype.Controller")
                || text.contains("org.springframework.web.bind.annotation");
    }

    private static List<String> forbiddenResolvedClasspathEntries() {
        return resolvedGuardClasspaths().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .flatMap(path -> forbiddenClasspathSignals(path)
                                .map(signal -> entry.getKey() + ": " + fileName(path) + " [" + signal.label() + "]")))
                .distinct()
                .sorted()
                .toList();
    }

    private static Map<String, List<Path>> resolvedGuardClasspaths() {
        Map<String, List<Path>> classpaths = new LinkedHashMap<>();
        for (String propertyName : GUARDED_CLASSPATH_PROPERTIES) {
            String propertyValue = requiredGuardClasspathProperty(propertyName);
            classpaths.put(propertyName, classpathEntries(propertyValue));
        }
        return classpaths;
    }

    private static void assertGuardClasspathPropertiesArePresent() {
        for (String propertyName : GUARDED_CLASSPATH_PROPERTIES) {
            List<Path> entries = classpathEntries(requiredGuardClasspathProperty(propertyName));
            assertFalse(entries.isEmpty(),
                    () -> propertyName + " must resolve to at least one classpath entry");
        }
    }

    private static String requiredGuardClasspathProperty(String propertyName) {
        String propertyValue = System.getProperty(propertyName);
        assertFalse(propertyValue == null || propertyValue.isBlank(),
                () -> propertyName + " must be supplied by the Gradle test task so the dependency guard cannot no-op");
        return propertyValue;
    }

    private static List<Path> classpathEntries(String classpath) {
        if (classpath == null || classpath.isBlank()) {
            return List.of();
        }
        return Arrays.stream(classpath.split(Pattern.quote(File.pathSeparator)))
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .toList();
    }

    private static Stream<ForbiddenClasspathSignal> forbiddenClasspathSignals(Path path) {
        String fileName = fileName(path).toLowerCase(Locale.ROOT);
        return FORBIDDEN_CLASSPATH_SIGNALS.stream()
                .filter(signal -> signal.matches(fileName));
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, NoPrometheusMvpPathTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private static boolean containsForbiddenResourceMvpSignal(Path resourceFile) {
        String normalizedPath = STARTER_ROOT.relativize(resourceFile).toString()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
        String text = readUnchecked(resourceFile).toLowerCase(Locale.ROOT);
        List<String> forbiddenSignals = List.of(
                "prometheus",
                "promql",
                "scrape_configs",
                "scrape_interval",
                "static_configs",
                "metrics_path",
                "honor_labels",
                "targets:",
                "scrape_config",
                "query-ui",
                "query ui",
                "query builder",
                "metric query",
                "metrics query",
                "/query",
                "/queries",
                "queryui",
                "query_ui",
                "querybuilder",
                "query_builder",
                "metric_query",
                "queries/",
                "query.html",
                "query.js",
                "static/query",
                "templates/query");
        return forbiddenSignals.stream().anyMatch(normalizedPath::contains)
                || forbiddenSignals.stream().anyMatch(text::contains);
    }

    private static boolean hasForbiddenMvpPathName(Path sourceFile) {
        String relativeName = STARTER_ROOT.relativize(sourceFile).toString()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
        List<String> forbiddenNameFragments = List.of(
                "prometheus",
                "promql",
                "scrape",
                "pullmetric",
                "pull-metric",
                "metricsexport",
                "metricexport",
                "prometheusquery",
                "pullmetricquery",
                "pull-metric-query",
                "querybuilder",
                "query-builder",
                "metricquery",
                "metric-query",
                "queryui",
                "query-ui",
                "querycontroller",
                "dashboardreadmodel",
                "readmodel",
                "read-model",
                "read_model",
                "endpointpriority",
                "priority",
                "lifecyclestate",
                "insightrule");
        boolean forbiddenPercentileName = relativeName.contains("p95")
                || relativeName.contains("percentile95")
                || relativeName.contains("percentile");
        return forbiddenNameFragments.stream().anyMatch(relativeName::contains)
                || forbiddenPercentileName && !isAllowedStarterLocalPercentilePath(sourceFile);
    }

    private static boolean containsForbiddenMvpPathCode(Path sourceFile) {
        return containsForbiddenMvpPathCodeText(
                readUnchecked(sourceFile),
                isAllowedStarterLocalPercentilePath(sourceFile));
    }

    private static boolean containsForbiddenMvpPathCodeText(String rawText) {
        return containsForbiddenMvpPathCodeText(rawText, false);
    }

    private static boolean containsForbiddenMvpPathCodeText(
            String rawText,
            boolean allowStarterLocalPercentile) {
        String codeText = stripComments(rawText);
        List<String> forbiddenSymbols = List.of(
                "Prometheus",
                "PromQL",
                "Promql",
                "QueryBuilder",
                "MetricQuery",
                "QueryUi",
                "queryBuilder",
                "DashboardReadModel",
                "EndpointPriority",
                "LifecycleState",
                "InsightRule",
                "PrometheusMeterRegistry",
                "CollectorRegistry",
                "PrometheusConfig",
                "PrometheusScrapeEndpoint");
        List<Pattern> forbiddenCodePatterns = List.of(
                Pattern.compile("\\b(class|interface|record|enum)\\s+\\w*(PrometheusQuery|PullMetricQuery|MetricQuery|QueryBuilder|QueryUi)\\w*"),
                Pattern.compile("\\b(class|interface|record|enum)\\s+\\w*(DashboardReadModel|EndpointPriority|LifecycleState|InsightRule)\\w*"),
                Pattern.compile("read[-_\\s]*model", Pattern.CASE_INSENSITIVE),
                Pattern.compile("priority", Pattern.CASE_INSENSITIVE));
        List<Pattern> forbiddenPercentilePatterns = List.of(
                Pattern.compile("p95", Pattern.CASE_INSENSITIVE),
                Pattern.compile("percentile\\w*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("calculate\\w*percentile", Pattern.CASE_INSENSITIVE));

        return forbiddenSymbols.stream().anyMatch(codeText::contains)
                || forbiddenCodePatterns.stream().anyMatch(pattern -> pattern.matcher(codeText).find())
                || !allowStarterLocalPercentile && forbiddenPercentilePatterns.stream()
                        .anyMatch(pattern -> pattern.matcher(codeText).find())
                || FORBIDDEN_ENDPOINT_PATH_FRAGMENTS.stream().anyMatch(codeText::contains);
    }

    private static boolean isAllowedStarterLocalPercentilePath(Path sourceFile) {
        String relativeName = STARTER_ROOT.relativize(sourceFile.toAbsolutePath().normalize()).toString()
                .replace('\\', '/');
        return ALLOWED_STARTER_LOCAL_PERCENTILE_PATHS.contains(relativeName);
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }

    private static String uncommentedLines(String text) {
        return stripComments(text).lines()
                .map(String::strip)
                .filter(line -> !line.startsWith("#"))
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private static String stripComments(String text) {
        StringBuilder result = new StringBuilder(text.length());
        CommentScanState state = CommentScanState.CODE;
        boolean escaped = false;
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';

            if (state == CommentScanState.CODE) {
                if (current == '/' && next == '/') {
                    result.append("  ");
                    index += 2;
                    state = CommentScanState.LINE_COMMENT;
                } else if (current == '/' && next == '*') {
                    result.append("  ");
                    index += 2;
                    state = CommentScanState.BLOCK_COMMENT;
                } else if (startsTextBlockDelimiter(text, index)) {
                    result.append("\"\"\"");
                    index += 3;
                    state = CommentScanState.TEXT_BLOCK;
                } else if (current == '"') {
                    result.append(current);
                    index++;
                    state = CommentScanState.STRING_LITERAL;
                    escaped = false;
                } else if (current == '\'') {
                    result.append(current);
                    index++;
                    state = CommentScanState.CHAR_LITERAL;
                    escaped = false;
                } else {
                    result.append(current);
                    index++;
                }
            } else if (state == CommentScanState.LINE_COMMENT) {
                if (current == '\n' || current == '\r') {
                    result.append(current);
                    index++;
                    state = CommentScanState.CODE;
                } else {
                    result.append(' ');
                    index++;
                }
            } else if (state == CommentScanState.BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    result.append("  ");
                    index += 2;
                    state = CommentScanState.CODE;
                } else if (current == '\n' || current == '\r') {
                    result.append(current);
                    index++;
                } else {
                    result.append(' ');
                    index++;
                }
            } else if (state == CommentScanState.STRING_LITERAL) {
                result.append(current);
                index++;
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    state = CommentScanState.CODE;
                }
            } else if (state == CommentScanState.CHAR_LITERAL) {
                result.append(current);
                index++;
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '\'') {
                    state = CommentScanState.CODE;
                }
            } else if (startsTextBlockDelimiter(text, index)) {
                result.append("\"\"\"");
                index += 3;
                state = CommentScanState.CODE;
            } else {
                result.append(current);
                index++;
            }
        }
        return result.toString();
    }

    private static boolean containsAnyPathSegment(Path path, List<String> segments) {
        return segments.stream().anyMatch(segment -> containsPathSegment(path, segment));
    }

    private static boolean containsPathSegment(Path path, String segment) {
        for (Path pathSegment : path) {
            if (segment.equals(pathSegment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPathSequence(Path path, String firstSegment, String secondSegment) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        for (int index = 0; index < normalizedPath.getNameCount() - 1; index++) {
            if (firstSegment.equals(normalizedPath.getName(index).toString())
                    && secondSegment.equals(normalizedPath.getName(index + 1).toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTestSourcePath(Path path) {
        return containsPathSequence(path, "src", "test");
    }

    private static boolean isSupportedMainSourceFile(Path path) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!containsMainSourceDirectory(normalizedPath)
                || containsPathSequence(normalizedPath, "src", "test")) {
            return false;
        }

        String fileName = fileName(normalizedPath).toLowerCase(Locale.ROOT);
        return MAIN_SOURCE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private static boolean containsMainSourceDirectory(Path path) {
        for (int index = 0; index < path.getNameCount() - 2; index++) {
            if ("src".equals(path.getName(index).toString())
                    && "main".equals(path.getName(index + 1).toString())
                    && MAIN_SOURCE_DIRECTORIES.contains(path.getName(index + 2).toString())) {
                return true;
            }
        }
        return false;
    }

    private static String sourceFileBaseName(Path path) {
        String fileName = fileName(path);
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart < 0) {
            return fileName;
        }
        return fileName.substring(0, extensionStart);
    }

    private static String fileName(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return path.toString();
        }
        return fileName.toString();
    }

    private static void assertRecordComponentsMatchAllowlist(Class<?> modelClass) {
        List<AllowedRecordComponent> allowedComponents = GUARDED_MODEL_COMPONENT_ALLOWLIST.get(modelClass);
        assertFalse(allowedComponents == null || allowedComponents.isEmpty(),
                () -> modelClass.getName() + " must have an explicit Story 2.6 component allowlist");
        assertRecordComponentsMatchAllowlist(modelClass, allowedComponents);
    }

    private static void assertRecordComponentsMatchAllowlist(
            Class<?> modelClass,
            List<AllowedRecordComponent> allowedComponents) {
        assertTrue(modelClass.isRecord(), () -> modelClass.getName() + " must remain a record model");

        List<AllowedRecordComponent> actualComponents = Arrays.stream(modelClass.getRecordComponents())
                .map(component -> new AllowedRecordComponent(
                        component.getName(),
                        component.getGenericType().getTypeName()))
                .toList();

        assertEquals(allowedComponents, actualComponents,
                () -> modelClass.getName()
                        + " record components must match the Story 2.6 allowlist exactly");
    }

    private static void assertCarrierFixtureRejected(
            Class<?> fixtureClass,
            AllowedRecordComponent allowedComponent) {
        assertThrows(AssertionError.class,
                () -> assertRecordComponentsMatchAllowlist(fixtureClass, List.of(allowedComponent)),
                () -> fixtureClass.getName() + " must be rejected by the model component allowlist fixture");
    }

    private static AllowedRecordComponent component(String name, Class<?> type) {
        return new AllowedRecordComponent(name, type.getTypeName());
    }

    private static AllowedRecordComponent listComponent(String name, Class<?> elementType) {
        return new AllowedRecordComponent(name, List.class.getTypeName() + "<" + elementType.getName() + ">");
    }

    private static AllowedRecordComponent optionalComponent(String name, Class<?> elementType) {
        return new AllowedRecordComponent(name, Optional.class.getTypeName() + "<" + elementType.getName() + ">");
    }

    private static boolean startsTextBlockDelimiter(String text, int index) {
        return index + 2 < text.length()
                && text.charAt(index) == '"'
                && text.charAt(index + 1) == '"'
                && text.charAt(index + 2) == '"'
                && !isEscaped(text, index);
    }

    private static boolean isEscaped(String text, int index) {
        int slashCount = 0;
        int cursor = index - 1;
        while (cursor >= 0 && text.charAt(cursor) == '\\') {
            slashCount++;
            cursor--;
        }
        return slashCount % 2 == 1;
    }

    private static List<String> relativePaths(List<Path> paths) {
        return paths.stream()
                .map(path -> STARTER_ROOT.relativize(path).toString())
                .toList();
    }

    private enum CommentScanState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING_LITERAL,
        CHAR_LITERAL,
        TEXT_BLOCK
    }

    private record AllowedRecordComponent(String name, String typeName) {
    }

    private record ForbiddenClasspathSignal(String label, Pattern fileNamePattern) {

        private ForbiddenClasspathSignal(String label, String fileNamePattern) {
            this(label, Pattern.compile(fileNamePattern, Pattern.CASE_INSENSITIVE));
        }

        private boolean matches(String fileName) {
            return fileNamePattern.matcher(fileName).matches();
        }
    }

    private record ObjectPayloadFixture(Object payload) {
    }

    private record PropertiesPayloadFixture(Properties payload) {
    }

    private record JsonNodePayloadFixture(JsonNode payload) {
    }

    private record ListStringValuesFixture(List<String> values) {
    }

    private record MapPayloadFixture(Map<String, String> payload) {
    }

    private record ArrayPayloadFixture(String[] payload) {
    }

    private record RawGenericCarrierFixture(List payload) {
    }

    private record NeutralNameCarrierFixture(Properties value) {
    }
}
