package com.observation.portal.domain.account.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * `account_project_memberships` table에 매핑되는 account-project authorization membership JPA entity다.
 *
 * <p>Project/Application/Instance catalog path 정합성이 아니라, Bearer account가 어떤 project에 접근 가능한지를
 * 저장하는 persistence model이다.</p>
 */
@Entity
@Table(name = "account_project_memberships")
public class AccountProjectMembershipEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected AccountProjectMembershipEntity() {
    }

    /**
     * local/dev/test fixture나 internal bootstrap path에서 명시적인 account-project membership row를 만든다.
     */
    public AccountProjectMembershipEntity(
            UUID id,
            UUID accountId,
            UUID projectId,
            String role,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        this.role = requireText(role, "role");
        this.status = requireText(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
