package com.observation.smoke.startertosnapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.PortalApplication;
import com.observation.portal.domain.account.service.ServiceTokenIssuer;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotLatestRow;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import com.observation.starter.service.StarterMetricIngestService;
import com.observation.starter.spring.web.StarterHttpServerObservationFilter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Story 6.0 Checkpoint 3의 starter 앱에서 portal snapshot까지 이어지는 opt-in E2E 검증이다.
 *
 * <p>public project 생성 API 없이 테스트 내부 seed로 프로젝트를 준비하고, raw project key는 런타임에만 생성해
 * 응답, snapshot JSON, 로그 capture에 남지 않는지 함께 확인한다.</p>
 */
@Tag("portal-e2e")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class StarterToPortalDashboardAxisSeparationE2ETest {

    private static final String APPLICATION_NAME = "starter-preflight-smoke";
    private static final String ENVIRONMENT = "preflight";
    private static final String INSTANCE = "story-6-0-instance";
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration BUCKET_TIMEOUT = Duration.ofSeconds(95);
    private static final Duration SNAPSHOT_TIMEOUT = Duration.ofSeconds(10);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Test
    void starterSmokeFlowShouldKeepHeartbeatConnectionAndAcceptedBucketMetricAxesSeparate(
            CapturedOutput capturedOutput) throws Exception {
        UUID projectId = UUID.randomUUID();
        String keyPrefix = "cp3" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String rawProjectKey = keyPrefix + "." + UUID.randomUUID().toString().replace("-", "");

        ConfigurableApplicationContext portal = null;
        ConfigurableApplicationContext smoke = null;
        try {
            portal = startPortal();
            seedProject(portal, projectId, keyPrefix, rawProjectKey);

            int portalPort = localPort(portal);
            smoke = startSmokeApp(portalPort, rawProjectKey);
            int smokePort = localPort(smoke);
            assertThat(smoke.getBeansOfType(FilterRegistrationBean.class).values())
                    .as("starter smoke app should register starter HTTP observation filter")
                    .anySatisfy(registration -> assertThat(registration.getFilter())
                            .isInstanceOf(StarterHttpServerObservationFilter.class));

            String pingBody = get("http://127.0.0.1:" + smokePort + "/smoke/ping");
            assertThat(objectMapper.readTree(pingBody).path("status").asText()).isEqualTo("ok");
            assertDoesNotExposeRuntimeProjectKey("smoke ping response", pingBody, rawProjectKey);

            StarterHeartbeatTelemetryRepository heartbeatRepository =
                    portal.getBean(StarterHeartbeatTelemetryRepository.class);
            StarterHeartbeatTelemetryRecord heartbeat = awaitValue(
                    "starter heartbeat persistence",
                    HEARTBEAT_TIMEOUT,
                    () -> heartbeatRepository.findByIdentity(projectId, APPLICATION_NAME, ENVIRONMENT, INSTANCE)
                            .orElse(null));

            assertThat(heartbeat.projectId()).isEqualTo(projectId);
            assertThat(heartbeat.applicationName()).isEqualTo(APPLICATION_NAME);
            assertThat(heartbeat.environment()).isEqualTo(ENVIRONMENT);
            assertThat(heartbeat.instanceName()).isEqualTo(INSTANCE);
            assertThat(heartbeat.heartbeatStatus()).isEqualTo("received");

            for (int index = 0; index < 4; index++) {
                assertDoesNotExposeRuntimeProjectKey(
                        "smoke ping response",
                        get("http://127.0.0.1:" + smokePort + "/smoke/ping"),
                        rawProjectKey);
            }

            ApplicationRepository applicationRepository = portal.getBean(ApplicationRepository.class);
            MetricBucketRepository metricBucketRepository = portal.getBean(MetricBucketRepository.class);
            StarterMetricIngestService starterMetricIngestService = smoke.getBean(StarterMetricIngestService.class);

            ApplicationEntity application = awaitValue(
                    "accepted metric bucket catalog row",
                    BUCKET_TIMEOUT,
                    () -> {
                        starterMetricIngestService.drainDueBuckets();
                        return applicationRepository
                                .findByProjectIdAndNameAndEnvironment(projectId, APPLICATION_NAME, ENVIRONMENT)
                                .orElse(null);
                    });
            OffsetDateTime latestBucketEndUtc = awaitValue(
                    "accepted metric bucket latest end boundary",
                    BUCKET_TIMEOUT,
                    () -> {
                        starterMetricIngestService.drainDueBuckets();
                        return metricBucketRepository.findLatestBucketEndUtcByApplicationId(application.id())
                                .orElse(null);
                    });

            String dashboardBody = get("http://127.0.0.1:" + portalPort
                    + "/api/projects/" + projectId
                    + "/applications/" + application.id()
                    + "/dashboard", portalAccessToken(portal));
            assertDoesNotExposeRuntimeProjectKey("dashboard current response", dashboardBody, rawProjectKey);
            JsonNode dashboard = objectMapper.readTree(dashboardBody);

            assertCurrentReadModelSeparatesAcceptedBucketAndStarterHeartbeatAxes(
                    dashboard,
                    latestBucketEndUtc);

            DashboardSnapshotRepository snapshotRepository = portal.getBean(DashboardSnapshotRepository.class);
            DashboardSnapshotDetailRow snapshot = awaitValue(
                    "dashboard snapshot row",
                    SNAPSHOT_TIMEOUT,
                    () -> snapshotRepository.findLatestByApplicationId(application.id())
                            .flatMap(latest -> detail(snapshotRepository, projectId, application.id(), latest))
                            .orElse(null));
            assertThat(snapshot.captureReason()).isEqualTo("query_fallback");
            assertDoesNotExposeRuntimeProjectKey("dashboard snapshot read_model_json",
                    snapshot.readModelJson(),
                    rawProjectKey);

            JsonNode storedReadModel = objectMapper.readTree(snapshot.readModelJson());
            assertSnapshotReadModelShapeAndAxisSeparation(storedReadModel, latestBucketEndUtc);
        } finally {
            close(smoke);
            close(portal);
        }

        assertDoesNotExposeRuntimeProjectKey("captured log and exception output",
                capturedOutput.getAll(),
                rawProjectKey);
    }

    private ConfigurableApplicationContext startPortal() {
        migratePortalSchema();
        return new SpringApplicationBuilder(PortalApplication.class)
                .run(
                        arg("server.port", "0"),
                        arg("spring.datasource.url", POSTGRES.getJdbcUrl()),
                        arg("spring.datasource.username", POSTGRES.getUsername()),
                        arg("spring.datasource.password", POSTGRES.getPassword()),
                        arg("spring.jpa.hibernate.ddl-auto", "none"),
                        arg("spring.main.banner-mode", "off"));
    }

    private static void migratePortalSchema() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    private ConfigurableApplicationContext startSmokeApp(int portalPort, String rawProjectKey) {
        return new SpringApplicationBuilder(StarterToSnapshotSmokeApplication.class)
                .run(
                        arg("server.port", "0"),
                        arg("spring.main.banner-mode", "off"),
                        arg("spring.application.name", APPLICATION_NAME),
                        arg("spring.autoconfigure.exclude", String.join(",",
                                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                                "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
                                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")),
                        arg("observation.heartbeat.portal-base-url", "http://127.0.0.1:" + portalPort),
                        arg("observation.heartbeat.project-key", rawProjectKey),
                        arg("observation.heartbeat.interval-seconds", "1"),
                        arg("observation.heartbeat.timeout-millis", "1000"),
                        arg("observation.metric-flush.project-id", "story-6-0"),
                        arg("observation.metric-flush.environment", ENVIRONMENT),
                        arg("observation.metric-flush.instance", INSTANCE));
    }

    private static String arg(String name, String value) {
        return "--" + name + "=" + value;
    }

    private static void seedProject(
            ConfigurableApplicationContext portal,
            UUID projectId,
            String keyPrefix,
            String rawProjectKey) {
        ProjectRepository projectRepository = portal.getBean(ProjectRepository.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        projectRepository.saveAndFlush(new ProjectEntity(
                projectId,
                "story-6-0-preflight-" + keyPrefix,
                keyPrefix,
                BCrypt.hashpw(rawProjectKey, BCrypt.gensalt()),
                "active",
                now,
                now));
    }

    private String get(String uri) throws Exception {
        return get(uri, null);
    }

    private String get(String uri, String bearerAccessToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (bearerAccessToken != null && !bearerAccessToken.isBlank()) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerAccessToken.trim());
        }
        HttpResponse<String> response = httpClient.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("HTTP GET %s should return 2xx", uri)
                .isBetween(200, 299);
        return response.body();
    }

    /**
     * portal resource API가 요구하는 service Bearer token을 테스트 내부에서 발급한다.
     */
    private static String portalAccessToken(ConfigurableApplicationContext portal) {
        ServiceTokenIssuer tokenIssuer = portal.getBean(ServiceTokenIssuer.class);
        return tokenIssuer.issue(UUID.randomUUID(), tokenIssuer.generateRefreshToken()).accessToken();
    }

    private static void assertCurrentReadModelSeparatesAcceptedBucketAndStarterHeartbeatAxes(
            JsonNode dashboard,
            OffsetDateTime latestBucketEndUtc) {
        assertJsonTimestampEquals(
                "dashboard application.lastAcceptedBucketAt",
                dashboard.path("application").path("lastAcceptedBucketAt"),
                latestBucketEndUtc);
        assertJsonTimestampEquals(
                "dashboard application.freshness.lastObservedAt",
                dashboard.path("application").path("freshness").path("lastObservedAt"),
                latestBucketEndUtc);
        assertThat(dashboard.path("metrics").path("requestCount").asLong()).isPositive();
        assertThat(dashboard.path("state").path("scope").asText()).isEqualTo("application");
        assertThat(dashboard.path("starterConnection").path("statusSource").asText())
                .isEqualTo("starter_heartbeat");
        assertThat(dashboard.path("starterConnection").path("lastHeartbeatStatus").asText())
                .isEqualTo("received");
        assertThat(dashboard.path("starterConnection").path("stateImpact").asText())
                .isEqualTo("none");
    }

    private static void assertSnapshotReadModelShapeAndAxisSeparation(
            JsonNode storedReadModel,
            OffsetDateTime latestBucketEndUtc) {
        assertJsonTimestampEquals(
                "snapshot application.lastAcceptedBucketAt",
                storedReadModel.path("application").path("lastAcceptedBucketAt"),
                latestBucketEndUtc);
        assertJsonTimestampEquals(
                "snapshot application.freshness.lastObservedAt",
                storedReadModel.path("application").path("freshness").path("lastObservedAt"),
                latestBucketEndUtc);
        assertThat(storedReadModel.path("state").path("scope").asText()).isEqualTo("application");
        assertThat(storedReadModel.path("starterConnection").path("statusSource").asText())
                .isEqualTo("starter_heartbeat");
        assertThat(storedReadModel.path("starterConnection").path("stateImpact").asText())
                .isEqualTo("none");
        assertThat(storedReadModel.path("metrics").path("requestCount").asLong()).isPositive();
        assertThat(storedReadModel.path("snapshotEndpointEvidence").path("source").asText())
                .isEqualTo("bounded_endpoint_evidence");
        assertThat(storedReadModel.path("instanceSummary").path("source").asText())
                .isEqualTo("bounded_instance_summary");
        assertThat(storedReadModel.path("instanceSummary").path("items").isArray()).isTrue();
        assertThat(storedReadModel.path("instanceSummary").path("items").size()).isPositive();

        JsonNode instance = storedReadModel.path("instanceSummary").path("items").get(0);
        assertThat(instance.path("metricData").path("statusSource").asText()).isEqualTo("accepted_bucket");
        assertJsonTimestampEquals(
                "snapshot instanceSummary.items[0].metricData.lastAcceptedBucketAt",
                instance.path("metricData").path("lastAcceptedBucketAt"),
                latestBucketEndUtc);
        assertThat(instance.path("starterConnection").path("statusSource").asText())
                .isEqualTo("starter_heartbeat");
        assertThat(instance.path("starterConnection").path("stateImpact").asText()).isEqualTo("none");
    }

    /**
     * API/Jackson과 수동 JSON writer의 ISO-8601 초 단위 표현 차이를 UTC instant 기준으로 비교한다.
     */
    private static void assertJsonTimestampEquals(String description, JsonNode timestamp, OffsetDateTime expected) {
        assertThat(timestamp.isTextual()).as(description + " should be an ISO-8601 timestamp").isTrue();
        assertThat(OffsetDateTime.parse(timestamp.asText()).toInstant())
                .as(description)
                .isEqualTo(expected.toInstant());
    }

    private static java.util.Optional<DashboardSnapshotDetailRow> detail(
            DashboardSnapshotRepository snapshotRepository,
            UUID projectId,
            UUID applicationId,
            DashboardSnapshotLatestRow latest) {
        return snapshotRepository.findDetailRow(projectId, applicationId, latest.snapshotId());
    }

    /**
     * 비동기 heartbeat/flush/fallback 경로가 성공할 때까지 짧게 polling한다.
     */
    private static <T> T awaitValue(String description, Duration timeout, Supplier<T> supplier)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        RuntimeException lastFailure = null;
        while (System.nanoTime() <= deadlineNanos) {
            try {
                T value = supplier.get();
                if (value != null) {
                    return value;
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
            Thread.sleep(250L);
        }
        AssertionError failure = new AssertionError(description + " was not observed within " + timeout);
        if (lastFailure != null) {
            failure.initCause(lastFailure);
        }
        throw failure;
    }

    private static int localPort(ConfigurableApplicationContext context) {
        Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        assertThat(port).as("Spring Boot random local server port").isNotNull().isPositive();
        return port;
    }

    private static void assertDoesNotExposeRuntimeProjectKey(String surface, String value, String rawProjectKey) {
        if (value != null && value.contains(rawProjectKey)) {
            fail(surface + " must not expose the runtime-generated raw project key");
        }
    }

    private static void close(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }
}
