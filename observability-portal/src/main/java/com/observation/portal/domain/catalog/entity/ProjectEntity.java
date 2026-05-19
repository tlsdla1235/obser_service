package com.observation.portal.domain.catalog.entity;

import com.observation.portal.domain.catalog.model.ProjectKeyCandidate;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Flyway가 생성한 `projects` table에 매핑되는 JPA entity다.
 *
 * <p>persistence 내부 구현 세부사항이므로 service/controller 외부 계약으로 직접 노출하지 않는다.</p>
 */
@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 32)
    private String keyPrefix;

    @Column(name = "project_key_hash", nullable = false, length = 255)
    private String projectKeyHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected ProjectEntity() {
    }

    /**
     * test fixture나 이후 catalog write path에서 application-generated UUID 기반 entity를 만든다.
     */
    public ProjectEntity(
            UUID id,
            String name,
            String keyPrefix,
            String projectKeyHash,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = requireText(name, "name");
        this.keyPrefix = requireText(keyPrefix, "keyPrefix");
        this.projectKeyHash = requireText(projectKeyHash, "projectKeyHash");
        this.status = requireText(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * service-facing repository가 반환할 검증 후보 모델로 변환한다.
     */
    public ProjectKeyCandidate toCandidate() {
        return new ProjectKeyCandidate(
                id,
                name,
                keyPrefix,
                projectKeyHash,
                ProjectStatus.fromDatabaseValue(status));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
