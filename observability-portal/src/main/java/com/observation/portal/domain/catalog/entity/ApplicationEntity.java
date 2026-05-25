package com.observation.portal.domain.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Flyway가 생성한 `applications` table에 매핑되는 JPA entity다.
 *
 * <p>accepted ingest가 들어온 application/environment 조합의 catalog identity와 last-seen 시각을 관리한다.</p>
 */
@Entity
@Table(name = "applications")
public class ApplicationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "environment", nullable = false, length = 80)
    private String environment;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "first_seen_at")
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected ApplicationEntity() {
    }

    /**
     * accepted ingest catalog 경로에서 application-generated UUID 기반 application row를 만든다.
     */
    public ApplicationEntity(
            UUID id,
            UUID projectId,
            String name,
            String environment,
            String status,
            OffsetDateTime firstSeenAt,
            OffsetDateTime lastSeenAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        this.name = requireText(name, "name");
        this.environment = requireText(environment, "environment");
        this.status = requireText(status, "status");
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * 새 accepted bucket 수용 시각을 catalog first/last seen 값에 반영한다.
     */
    public void markSeenAt(OffsetDateTime seenAt) {
        OffsetDateTime requiredSeenAt = Objects.requireNonNull(seenAt, "seenAt must not be null");
        if (firstSeenAt == null || requiredSeenAt.isBefore(firstSeenAt)) {
            firstSeenAt = requiredSeenAt;
        }
        if (lastSeenAt == null || requiredSeenAt.isAfter(lastSeenAt)) {
            lastSeenAt = requiredSeenAt;
        }
        updatedAt = requiredSeenAt;
    }

    public UUID id() {
        return id;
    }

    /**
     * navigation read model과 catalog 조회가 속한 project id를 반환한다.
     */
    public UUID projectId() {
        return projectId;
    }

    /**
     * application/environment identity 중 application name을 반환한다.
     */
    public String name() {
        return name;
    }

    /**
     * application/environment identity 중 environment 값을 반환한다.
     */
    public String environment() {
        return environment;
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
