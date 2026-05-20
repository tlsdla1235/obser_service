package com.observation.portal.domain.bucket.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.bucket.entity.AcceptedMetricBucketEntity;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.catalog.model.ApplicationCatalogEntry;
import com.observation.portal.domain.catalog.repository.ApplicationCatalogRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * validated metric bucket을 catalog row와 연결해 PostgreSQL에 저장하는 repository layer facade다.
 *
 * <p>JPA entity와 Spring Data repository를 내부 구현으로 숨기고, service에는 receipt 모델만 반환한다.</p>
 */
@Repository
public class MetricBucketRepository {

    private final AcceptedMetricBucketJpaRepository acceptedMetricBucketJpaRepository;
    private final ApplicationCatalogRepository applicationCatalogRepository;
    private final ObjectMapper objectMapper;

    /**
     * bucket 저장과 catalog get-or-create에 필요한 repository 구현체를 주입한다.
     */
    public MetricBucketRepository(
            AcceptedMetricBucketJpaRepository acceptedMetricBucketJpaRepository,
            ApplicationCatalogRepository applicationCatalogRepository,
            ObjectMapper objectMapper) {
        this.acceptedMetricBucketJpaRepository = Objects.requireNonNull(
                acceptedMetricBucketJpaRepository,
                "acceptedMetricBucketJpaRepository must not be null");
        this.applicationCatalogRepository = Objects.requireNonNull(
                applicationCatalogRepository,
                "applicationCatalogRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * application/application instance catalog row를 찾거나 만든 뒤 accepted bucket을 insert한다.
     */
    @Transactional
    public AcceptedMetricBucketReceipt insert(AcceptedMetricBucketWriteCommand command) {
        AcceptedMetricBucketWriteCommand requiredCommand = Objects.requireNonNull(
                command,
                "command must not be null");
        ApplicationCatalogEntry catalogEntry = applicationCatalogRepository.getOrCreate(
                requiredCommand.projectId(),
                requiredCommand.applicationName(),
                requiredCommand.environment(),
                requiredCommand.instanceName(),
                requiredCommand.acceptedAt());

        AcceptedMetricBucketEntity entity = new AcceptedMetricBucketEntity(
                UUID.randomUUID(),
                requiredCommand.projectId(),
                catalogEntry.applicationId(),
                catalogEntry.applicationInstanceId(),
                requiredCommand.schemaVersion(),
                requiredCommand.idempotencyKey(),
                requiredCommand.payloadHash(),
                requiredCommand.bucketStartUtc(),
                requiredCommand.bucketEndUtc(),
                requiredCommand.durationSeconds(),
                requiredCommand.acceptedAt(),
                requiredCommand.requestCount(),
                requiredCommand.errorCount(),
                writeJson(requiredCommand.durationBuckets()),
                toBigDecimal(requiredCommand.cpuUsageRatio()),
                toBigDecimal(requiredCommand.heapUsedRatio()),
                toBigDecimal(requiredCommand.datasourcePoolUsageRatio()),
                writeJson(requiredCommand.endpoints()),
                requiredCommand.acceptedAt());

        return acceptedMetricBucketJpaRepository.saveAndFlush(entity).toReceipt();
    }

    /**
     * project scope idempotency key로 이미 저장된 bucket receipt를 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<AcceptedMetricBucketReceipt> findByProjectIdAndIdempotencyKey(
            UUID projectId,
            String idempotencyKey) {
        return acceptedMetricBucketJpaRepository.findByProjectIdAndIdempotencyKey(projectId, idempotencyKey)
                .map(AcceptedMetricBucketEntity::toReceipt);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("accepted bucket JSON serialization failed", exception);
        }
    }

    private static BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
