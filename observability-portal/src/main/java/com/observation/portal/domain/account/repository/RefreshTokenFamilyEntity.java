package com.observation.portal.domain.account.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * refresh token rotation family metadata를 저장하는 JPA entity다.
 *
 * <p>reuse detection이 발생하면 family 전체 상태를 표시해 후속 revoke 정책을 적용할 수 있게 한다.</p>
 */
@Entity
@Table(name = "refresh_token_families")
public class RefreshTokenFamilyEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected RefreshTokenFamilyEntity() {
    }

    private RefreshTokenFamilyEntity(UUID id, UUID accountId, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.status = requireText(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * 로그인 성공 시 새 refresh token family를 만든다.
     */
    public static RefreshTokenFamilyEntity active(UUID id, UUID accountId, OffsetDateTime now) {
        return new RefreshTokenFamilyEntity(id, accountId, "active", now, now);
    }

    /**
     * reuse detection 발생 시 family를 재사용 감지 상태로 전환한다.
     */
    public void markReuseDetected(OffsetDateTime now) {
        this.status = "reuse_detected";
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * logout 또는 explicit revoke 시 family를 revoked 상태로 전환한다.
     */
    public void markRevoked(OffsetDateTime now) {
        this.status = "revoked";
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * refresh token rotation을 계속 허용할 수 있는 active family인지 확인한다.
     */
    public boolean isActive() {
        return "active".equals(status);
    }

    /**
     * refresh token family 기본키를 반환한다.
     */
    public UUID id() {
        return id;
    }

    /**
     * family를 소유한 내부 account 기본키를 반환한다.
     */
    public UUID accountId() {
        return accountId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
