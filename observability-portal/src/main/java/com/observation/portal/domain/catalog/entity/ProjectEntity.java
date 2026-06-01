package com.observation.portal.domain.catalog.entity;

import com.observation.portal.domain.catalog.model.ProjectKeyCandidate;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.catalog.model.StarterCredentialStatus;
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

    @Column(name = "starter_credential_status", nullable = false, length = 32)
    private String starterCredentialStatus;

    @Column(name = "starter_credential_issued_at", nullable = false)
    private OffsetDateTime starterCredentialIssuedAt;

    @Column(name = "starter_credential_rotated_at")
    private OffsetDateTime starterCredentialRotatedAt;

    @Column(name = "starter_credential_revoked_at")
    private OffsetDateTime starterCredentialRevokedAt;

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
        this(
                id,
                name,
                keyPrefix,
                projectKeyHash,
                status,
                StarterCredentialStatus.ACTIVE.databaseValue(),
                createdAt,
                null,
                null,
                createdAt,
                updatedAt);
    }

    /**
     * starter credential lifecycle metadata까지 명시해 project persistence row를 만든다.
     */
    public ProjectEntity(
            UUID id,
            String name,
            String keyPrefix,
            String projectKeyHash,
            String status,
            String starterCredentialStatus,
            OffsetDateTime starterCredentialIssuedAt,
            OffsetDateTime starterCredentialRotatedAt,
            OffsetDateTime starterCredentialRevokedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = requireText(name, "name");
        this.keyPrefix = requireText(keyPrefix, "keyPrefix");
        this.projectKeyHash = requireText(projectKeyHash, "projectKeyHash");
        this.status = requireText(status, "status");
        this.starterCredentialStatus = requireText(starterCredentialStatus, "starterCredentialStatus");
        this.starterCredentialIssuedAt = Objects.requireNonNull(
                starterCredentialIssuedAt,
                "starterCredentialIssuedAt must not be null");
        this.starterCredentialRotatedAt = starterCredentialRotatedAt;
        this.starterCredentialRevokedAt = starterCredentialRevokedAt;
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
                ProjectStatus.fromDatabaseValue(status),
                StarterCredentialStatus.fromDatabaseValue(starterCredentialStatus),
                starterCredentialIssuedAt,
                starterCredentialRotatedAt,
                starterCredentialRevokedAt);
    }

    /**
     * starter credential rotation 성공 시 새 prefix/hash와 lifecycle metadata로 현재 credential을 교체한다.
     *
     * <p>기존 raw credential은 별도 저장소에 없고, 이 변경으로 이전 prefix/hash는 즉시 검증 후보에서 사라진다.</p>
     */
    public void rotateStarterCredential(String newKeyPrefix, String newProjectKeyHash, OffsetDateTime rotatedAt) {
        OffsetDateTime requiredRotatedAt = Objects.requireNonNull(rotatedAt, "rotatedAt must not be null");
        this.keyPrefix = requireText(newKeyPrefix, "newKeyPrefix");
        this.projectKeyHash = requireText(newProjectKeyHash, "newProjectKeyHash");
        this.starterCredentialStatus = StarterCredentialStatus.ACTIVE.databaseValue();
        this.starterCredentialIssuedAt = requiredRotatedAt;
        this.starterCredentialRotatedAt = requiredRotatedAt;
        this.starterCredentialRevokedAt = null;
        this.updatedAt = requiredRotatedAt;
    }

    /**
     * project 표시 상태는 유지하면서 starter ingest credential만 폐기한다.
     */
    public void revokeStarterCredential(OffsetDateTime revokedAt) {
        OffsetDateTime requiredRevokedAt = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        this.starterCredentialStatus = StarterCredentialStatus.REVOKED.databaseValue();
        this.starterCredentialRevokedAt = requiredRevokedAt;
        this.updatedAt = requiredRevokedAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
