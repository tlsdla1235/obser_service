package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.snapshot.entity.DashboardSnapshotEntity;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteCommand;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteResult;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteValues;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * `dashboard_snapshots` row를 priority-aware idempotent upsert로 저장하는 writer service다.
 *
 * <p>duplicate identity는 `application_id + current_window_end_utc`이며, lower/equal priority incoming write는 기존
 * 대표 row와 helper column을 downgrade하지 않는다.</p>
 */
@Service
public class DashboardSnapshotWriterService {

    private static final Logger log = LoggerFactory.getLogger(DashboardSnapshotWriterService.class);
    private static final String DUPLICATE_IDENTITY_CONSTRAINT =
            "uk_dashboard_snapshots_application_current_window_end";
    private static final String SQL_STATE_UNIQUE_VIOLATION = "23505";
    private static final String OPERATION_UPSERT = "upsert";
    private static final String FAILURE_DUPLICATE_CONFLICT = "duplicate_conflict";
    private static final String FAILURE_SERIALIZATION = "serialization";
    private static final String FAILURE_PERSISTENCE = "persistence";
    private static final String FAILURE_UNKNOWN = "unknown";

    private final DashboardSnapshotRepository snapshotRepository;
    private final DashboardSnapshotReadModelEnricher enricher;
    private final DashboardSnapshotCapturePolicy capturePolicy;
    private final DashboardSnapshotWriteMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    /**
     * writer가 사용할 repository, JSON enricher, metric recorder, transaction boundary를 주입한다.
     */
    public DashboardSnapshotWriterService(
            DashboardSnapshotRepository snapshotRepository,
            DashboardSnapshotReadModelEnricher enricher,
            DashboardSnapshotCapturePolicy capturePolicy,
            DashboardSnapshotWriteMetrics metrics,
            PlatformTransactionManager transactionManager) {
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository must not be null");
        this.enricher = Objects.requireNonNull(enricher, "enricher must not be null");
        this.capturePolicy = Objects.requireNonNull(capturePolicy, "capturePolicy must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * dashboard read model을 bounded snapshot JSON으로 만든 뒤 identity 기준으로 insert/update/no-op에 수렴시킨다.
     */
    public DashboardSnapshotWriteResult write(DashboardSnapshotWriteCommand command) {
        DashboardSnapshotWriteCommand requiredCommand = Objects.requireNonNull(command, "command must not be null");
        DashboardSnapshotCaptureReason representativeReason = capturePolicy.representativeReason(requiredCommand);
        DashboardSnapshotWriteValues values = values(requiredCommand, representativeReason);
        return writeValues(representativeReason, values, true);
    }

    private DashboardSnapshotWriteValues values(
            DashboardSnapshotWriteCommand command,
            DashboardSnapshotCaptureReason representativeReason) {
        try {
            DashboardSnapshotReadModelEnricher.EnrichedSnapshotReadModel enriched = enricher.enrich(command);
            ApplicationDashboardReadModel readModel = command.readModel();
            ApplicationDashboardReadModel.SourceWindow sourceWindow = readModel.application().sourceWindow();
            ApplicationDashboardReadModel.Window baselineWindow = compatibilityBaselineWindow(sourceWindow);
            OffsetDateTime lastObservedAt = toUtc(readModel.application().lastAcceptedBucketAt());
            return new DashboardSnapshotWriteValues(
                    UUID.randomUUID(),
                    command.projectId(),
                    command.applicationId(),
                    toUtc(readModel.generatedAt()),
                    toUtc(sourceWindow.current().startUtc()),
                    toUtc(command.currentWindowEndUtc()),
                    toUtc(baselineWindow.startUtc()),
                    toUtc(baselineWindow.endUtc()),
                    lastObservedAt,
                    lastObservedAt,
                    readModel.state().code(),
                    representativeReason.token(),
                    enriched.primaryRuleId(),
                    enriched.primaryEndpointKey(),
                    enriched.maxConfidence(),
                    enriched.readModelJson(),
                    toUtc(command.requestedAt()));
        } catch (JsonProcessingException exception) {
            recordFailure(representativeReason, fallbackValues(command, representativeReason), FAILURE_SERIALIZATION, exception);
            throw new DashboardSnapshotWriteException(
                    "dashboard snapshot read model serialization failed",
                    FAILURE_SERIALIZATION,
                    exception);
        } catch (RuntimeException exception) {
            recordFailure(representativeReason, fallbackValues(command, representativeReason), FAILURE_UNKNOWN, exception);
            throw exception;
        }
    }

    private DashboardSnapshotWriteResult writeValues(
            DashboardSnapshotCaptureReason incomingReason,
            DashboardSnapshotWriteValues values,
            boolean retryOnDuplicateConflict) {
        try {
            DashboardSnapshotWriteResult result = transactionTemplate.execute(status ->
                    upsert(incomingReason, values));
            DashboardSnapshotWriteResult requiredResult = Objects.requireNonNull(
                    result,
                    "transaction result must not be null");
            metrics.recordSuccess(incomingReason.token(), requiredResult.operation().metricTag());
            return requiredResult;
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateIdentityConflict(exception)) {
                if (retryOnDuplicateConflict) {
                    return writeValues(incomingReason, values, false);
                }
                recordFailure(incomingReason, values, FAILURE_DUPLICATE_CONFLICT, exception);
                throw new DashboardSnapshotWriteException(
                        "dashboard snapshot duplicate identity conflict",
                        FAILURE_DUPLICATE_CONFLICT,
                        exception);
            }
            recordFailure(incomingReason, values, FAILURE_PERSISTENCE, exception);
            throw new DashboardSnapshotWriteException(
                    "dashboard snapshot persistence failed",
                    FAILURE_PERSISTENCE,
                    exception);
        } catch (DataAccessException exception) {
            recordFailure(incomingReason, values, FAILURE_PERSISTENCE, exception);
            throw new DashboardSnapshotWriteException(
                    "dashboard snapshot persistence failed",
                    FAILURE_PERSISTENCE,
                    exception);
        } catch (DashboardSnapshotWriteException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(incomingReason, values, FAILURE_UNKNOWN, exception);
            throw new DashboardSnapshotWriteException(
                    "dashboard snapshot write failed",
                    FAILURE_UNKNOWN,
                    exception);
        }
    }

    /**
     * 짧은 retry가 허용되는 writer identity unique constraint 충돌인지 cause chain에서 판정한다.
     */
    private static boolean isDuplicateIdentityConflict(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && DUPLICATE_IDENTITY_CONSTRAINT.equals(constraintViolation.getConstraintName())) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && SQL_STATE_UNIQUE_VIOLATION.equals(sqlException.getSQLState())
                    && messageContainsDuplicateIdentityConstraint(sqlException)) {
                return true;
            }
            if (messageContainsDuplicateIdentityConstraint(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean messageContainsDuplicateIdentityConstraint(Throwable exception) {
        String message = exception.getMessage();
        return message != null && message.contains(DUPLICATE_IDENTITY_CONSTRAINT);
    }

    private DashboardSnapshotWriteResult upsert(
            DashboardSnapshotCaptureReason incomingReason,
            DashboardSnapshotWriteValues values) {
        return snapshotRepository.findByIdentityForUpdate(values.applicationId(), values.currentWindowEndUtc())
                .map(existing -> updateOrNoop(incomingReason, values, existing))
                .orElseGet(() -> inserted(incomingReason, values));
    }

    private DashboardSnapshotWriteResult inserted(
            DashboardSnapshotCaptureReason incomingReason,
            DashboardSnapshotWriteValues values) {
        DashboardSnapshotEntity inserted = snapshotRepository.insert(values);
        return new DashboardSnapshotWriteResult(
                inserted.id(),
                DashboardSnapshotWriteResult.Operation.INSERT,
                incomingReason,
                inserted.currentWindowEndUtc(),
                inserted.generatedAt());
    }

    private DashboardSnapshotWriteResult updateOrNoop(
            DashboardSnapshotCaptureReason incomingReason,
            DashboardSnapshotWriteValues values,
            DashboardSnapshotEntity existing) {
        if (!incomingReason.outranksPersistedToken(existing.captureReason())) {
            return new DashboardSnapshotWriteResult(
                    existing.id(),
                    DashboardSnapshotWriteResult.Operation.NOOP,
                    incomingReason,
                    existing.currentWindowEndUtc(),
                    existing.generatedAt());
        }
        existing.updateRepresentative(values);
        DashboardSnapshotEntity updated = snapshotRepository.saveUpdated(existing);
        return new DashboardSnapshotWriteResult(
                updated.id(),
                DashboardSnapshotWriteResult.Operation.UPDATE,
                incomingReason,
                updated.currentWindowEndUtc(),
                updated.generatedAt());
    }

    private void recordFailure(
            DashboardSnapshotCaptureReason captureReason,
            DashboardSnapshotWriteValues values,
            String failureType,
            Throwable exception) {
        metrics.recordFailure(captureReason.token(), OPERATION_UPSERT, failureType);
        log.warn(
                "dashboard_snapshot_write_failed captureReason={} operation={} failureType={} applicationId={} currentWindowEndUtc={}",
                captureReason.token(),
                OPERATION_UPSERT,
                failureType,
                values.applicationId(),
                values.currentWindowEndUtc(),
                exception);
    }

    private static DashboardSnapshotWriteValues fallbackValues(
            DashboardSnapshotWriteCommand command,
            DashboardSnapshotCaptureReason representativeReason) {
        ApplicationDashboardReadModel readModel = command.readModel();
        ApplicationDashboardReadModel.SourceWindow sourceWindow = readModel.application().sourceWindow();
        ApplicationDashboardReadModel.Window baselineWindow = compatibilityBaselineWindow(sourceWindow);
        return new DashboardSnapshotWriteValues(
                UUID.randomUUID(),
                command.projectId(),
                command.applicationId(),
                toUtc(readModel.generatedAt()),
                toUtc(sourceWindow.current().startUtc()),
                toUtc(command.currentWindowEndUtc()),
                toUtc(baselineWindow.startUtc()),
                toUtc(baselineWindow.endUtc()),
                toUtc(readModel.application().lastAcceptedBucketAt()),
                toUtc(readModel.application().lastAcceptedBucketAt()),
                readModel.state().code(),
                representativeReason.token(),
                null,
                null,
                null,
                "{}",
                toUtc(command.requestedAt()));
    }

    /**
     * public read model에서는 baseline을 null로 둘 수 있지만, 기존 snapshot schema의 not-null helper column에는
     * legacy compatibility window를 채운다. 이 값은 MVP 판단 근거가 아니라 row shape 보존용 metadata다.
     */
    private static ApplicationDashboardReadModel.Window compatibilityBaselineWindow(
            ApplicationDashboardReadModel.SourceWindow sourceWindow) {
        if (sourceWindow.baseline() != null) {
            return sourceWindow.baseline();
        }
        ApplicationDashboardReadModel.Window currentWindow = sourceWindow.current();
        Duration currentDuration = Duration.between(currentWindow.startUtc(), currentWindow.endUtc());
        return new ApplicationDashboardReadModel.Window(
                currentWindow.startUtc().minus(currentDuration),
                currentWindow.startUtc());
    }

    private static OffsetDateTime toUtc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
