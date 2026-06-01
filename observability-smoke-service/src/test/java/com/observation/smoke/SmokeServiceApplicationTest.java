package com.observation.smoke;

import com.observation.starter.client.PortalMetricBucketClient;
import com.observation.starter.client.http.JdkPortalMetricBucketClient;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.service.MetricBucketRollupService;
import com.observation.starter.spring.observation.MicrometerHttpServerObservationBinder;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.web.filter.ServerHttpObservationFilter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=local-smoke",
        "OBSERVATION_PORTAL_BASE_URL=http://127.0.0.1:1",
        "OBSERVATION_SMOKE_PROJECT_KEY=smoke-test-key.fixture"
})
class SmokeServiceApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired
    private ServerHttpObservationFilter serverHttpObservationFilter;

    @Autowired
    private MetricBucketRollupService rollupService;

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
    }

    @Test
    void realMvcSmokeOkRequestIsRecordedByStarterCollectorPath() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + localServerPort + "/smoke/ok"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        List<ClosedMetricBucket> closedBuckets = rollupService.drainClosedBuckets(Instant.now().plusSeconds(90));

        assertThat(closedBuckets)
                .as("real MVC /smoke/ok request should pass through ServerHttpObservationFilter into starter rollup")
                .isNotEmpty();
        assertThat(closedBuckets.stream()
                .mapToLong(bucket -> bucket.appSummary().requestCount())
                .sum())
                .as("one real /smoke/ok request must be recorded exactly once")
                .isEqualTo(1L);
        assertThat(closedBuckets.stream()
                .flatMap(bucket -> bucket.endpointRollups().stream())
                .filter(endpoint -> "GET".equals(endpoint.endpointKey().method()))
                .mapToLong(endpoint -> endpoint.requestCount())
                .sum())
                .as("endpoint rollup should not double-count the single smoke request")
                .isEqualTo(1L);
    }

    private static Observation.Context httpServerContext() {
        Observation.Context context = new Observation.Context();
        context.setName("http.server.requests");
        return context;
    }
}
