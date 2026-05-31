package com.observation.portal.domain.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Flyway가 생성한 `application_instances` table에 매핑되는 JPA entity다.
 *
 * <p>accepted ingest를 보낸 실행 인스턴스 이름과 first/last seen 시각을 저장한다.</p>
 */
@Entity
@Table(name = "application_instances")
public class ApplicationInstanceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "instance_name", nullable = false, length = 200)
    private String instanceName;

    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected ApplicationInstanceEntity() {
    }

    /**
     * accepted ingest catalog 경로에서 application-generated UUID 기반 instance row를 만든다.
     */
    public ApplicationInstanceEntity(
            UUID id,
            UUID applicationId,
            String instanceName,
            OffsetDateTime firstSeenAt,
            OffsetDateTime lastSeenAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.applicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        this.instanceName = requireText(instanceName, "instanceName");
        this.firstSeenAt = Objects.requireNonNull(firstSeenAt, "firstSeenAt must not be null");
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * 새 accepted bucket 수용 시각을 instance first/last seen 값에 반영한다.
     */
    public void markSeenAt(OffsetDateTime seenAt) {
        OffsetDateTime requiredSeenAt = Objects.requireNonNull(seenAt, "seenAt must not be null");
        if (requiredSeenAt.isBefore(firstSeenAt)) {
            firstSeenAt = requiredSeenAt;
        }
        if (requiredSeenAt.isAfter(lastSeenAt)) {
            lastSeenAt = requiredSeenAt;
        }
        updatedAt = requiredSeenAt;
    }

    public UUID id() {
        return id;
    }

    /**
     * instance/application catalog path 정합성 검증과 evidence link 생성을 위해 부모 application id를 반환한다.
     */
    public UUID applicationId() {
        return applicationId;
    }

    /**
     * 화면 표시와 starter heartbeat identity lookup에 사용하는 instance name을 반환한다.
     */
    public String instanceName() {
        return instanceName;
    }

    public OffsetDateTime firstSeenAt() {
        return firstSeenAt;
    }

    public OffsetDateTime lastSeenAt() {
        return lastSeenAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
