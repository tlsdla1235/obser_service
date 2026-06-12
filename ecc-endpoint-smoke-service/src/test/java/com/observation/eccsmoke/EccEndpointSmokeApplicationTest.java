package com.observation.eccsmoke;

import com.observation.starter.client.PortalMetricBucketClient;
import com.observation.starter.client.http.JdkPortalMetricBucketClient;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.service.MetricBucketRollupService;
import com.observation.starter.service.StarterResourceMetricSampler;
import com.observation.starter.spring.StarterResourceMetricSamplerScheduler;
import com.observation.starter.spring.observation.MicrometerHttpServerObservationBinder;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.filter.ServerHttpObservationFilter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=local-ecc",
        "OBSERVATION_PORTAL_BASE_URL=http://127.0.0.1:1",
        "ECC_ENDPOINT_SMOKE_PROJECT_KEY=ecc-smoke-test-key.fixture"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EccEndpointSmokeApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired
    private ServerHttpObservationFilter serverHttpObservationFilter;

    @Autowired
    private MetricBucketRollupService rollupService;

    @Autowired
    private StarterResourceMetricSampler resourceMetricSampler;

    @LocalServerPort
    private int localServerPort;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void contextLoadsWithStarterDefaultClientAndMvcObservationInfrastructure() {
        Map<String, PortalMetricBucketClient> bucketClients =
                applicationContext.getBeansOfType(PortalMetricBucketClient.class);
        Map<String, ObservationHandler> observationHandlers =
                applicationContext.getBeansOfType(ObservationHandler.class);

        assertThat(observationRegistry).isNotNull();
        assertThat(serverHttpObservationFilter).isNotNull();
        assertThat(bucketClients).hasSize(1);
        assertThat(bucketClients.values().iterator().next()).isInstanceOf(JdkPortalMetricBucketClient.class);
        assertThat(observationHandlers.values())
                .filteredOn(handler -> handler instanceof MicrometerHttpServerObservationBinder)
                .hasSize(1)
                .allSatisfy(handler -> assertThat(handler.supportsContext(httpServerContext())).isTrue());
        assertThat(applicationContext.getBean(StarterResourceMetricSamplerScheduler.class)).isNotNull();
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
    }

    @Test
    void realMvcEccRequestIsRecordedByStarterCollectorPath() throws Exception {
        HttpResponse<String> response = sendGet("/api/auth/signup/check-id?studentId=20201234");

        assertThat(response.statusCode()).isEqualTo(200);
        List<ClosedMetricBucket> closedBuckets = rollupService.drainClosedBuckets(Instant.now().plusSeconds(90));

        assertThat(closedBuckets)
                .as("real MVC ECC-shaped request should pass through ServerHttpObservationFilter into starter rollup")
                .isNotEmpty();
        assertThat(closedBuckets.stream()
                .mapToLong(bucket -> bucket.appSummary().requestCount())
                .sum())
                .as("one real ECC-shaped request must be recorded exactly once")
                .isEqualTo(1L);
        assertThat(closedBuckets.stream()
                .flatMap(bucket -> bucket.endpointRollups().stream())
                .filter(endpoint -> "GET".equals(endpoint.endpointKey().method()))
                .mapToLong(endpoint -> endpoint.requestCount())
                .sum())
                .as("endpoint rollup should not double-count the single request")
                .isEqualTo(1L);
    }

    @Test
    void realMvcEccRequestAndResourceSamplerRecordJvmEvidenceWithoutDatasource() throws Exception {
        HttpResponse<String> response = sendGet("/api/auth/signup/check-id?studentId=20201234");

        assertThat(response.statusCode()).isEqualTo(200);
        resourceMetricSampler.sampleAndRecord();

        List<ClosedMetricBucket> closedBuckets = rollupService.drainClosedBuckets(Instant.now().plusSeconds(90));

        assertThat(closedBuckets)
                .as("ECC smoke should produce closed buckets after a request and a resource sampler tick")
                .isNotEmpty();
        assertThat(closedBuckets.stream()
                .filter(bucket -> bucket.appSummary().jvm().isPresent())
                .toList())
                .as("datasource-less ECC smoke still needs CPU/heap evidence in starter closed buckets")
                .isNotEmpty();
        assertThat(closedBuckets.stream()
                .filter(bucket -> bucket.appSummary().datasource().isPresent())
                .toList())
                .as("ECC smoke has no DataSource, so datasource pool evidence should stay absent")
                .isEmpty();
    }

    @Test
    void realMvcEccRequestsKeepMatchedRoutePatternsInEndpointRollups() throws Exception {
        assertThat(sendGet("/api/auth/signup/check-id?studentId=20201234").statusCode()).isEqualTo(200);
        assertThat(sendGet("/api/admin/users/1").statusCode()).isEqualTo(200);
        assertThat(sendGet("/api/ecc-smoke/error-500").statusCode()).isEqualTo(500);

        List<String> endpointKeys = rollupService.drainClosedBuckets(Instant.now().plusSeconds(90)).stream()
                .flatMap(bucket -> bucket.endpointRollups().stream())
                .map(endpoint -> endpoint.endpointKey().value())
                .toList();

        assertThat(endpointKeys)
                .as("Spring MVC matched route patterns should survive starter normalization before ingest")
                .contains(
                        "GET /api/auth/signup/check-id",
                        "GET /api/admin/users/{uuid}",
                        "GET /api/ecc-smoke/error-500")
                .doesNotContain("GET UNKNOWN");
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + localServerPort + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Observation.Context httpServerContext() {
        Observation.Context context = new Observation.Context();
        context.setName("http.server.requests");
        return context;
    }
}
