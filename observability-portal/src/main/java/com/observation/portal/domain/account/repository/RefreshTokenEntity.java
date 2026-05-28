package com.observation.portal.domain.account.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * refresh token 원문 대신 SHA-256 hash와 rotation 상태만 저장하는 JPA entity다.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "reuse_detected_at")
    private OffsetDateTime reuseDetectedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected RefreshTokenEntity() {
    }

    private RefreshTokenEntity(
            UUID id,
            UUID familyId,
            UUID accountId,
            String tokenHash,
            String status,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.tokenHash = requireHash(tokenHash);
        this.status = requireText(status, "status");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * 새 active refresh token metadata row를 만든다.
     */
    public static RefreshTokenEntity active(
            UUID id,
            UUID familyId,
            UUID accountId,
            String tokenHash,
            OffsetDateTime expiresAt,
            OffsetDateTime now) {
        return new RefreshTokenEntity(id, familyId, accountId, tokenHash, "active", expiresAt, now);
    }

    /**
     * 현재 시각 기준으로 rotation 가능한 active token인지 확인한다.
     */
    public boolean isActiveAt(OffsetDateTime now) {
        return "active".equals(status) && consumedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    /**
     * 정상 refresh 성공 시 기존 token을 consumed 상태로 바꾼다.
     */
    public void markConsumed(OffsetDateTime now) {
        this.status = "rotated";
        this.consumedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * logout 또는 explicit revoke 시 token을 revoked 상태로 바꾼다.
     */
    public void markRevoked(OffsetDateTime now) {
        this.status = "revoked";
        this.revokedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * 이미 사용됐거나 만료된 token 재사용을 감지했을 때 표시한다.
     */
    public void markReuseDetected(OffsetDateTime now) {
        this.status = "reuse_detected";
        this.reuseDetectedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * refresh token family 기본키를 반환한다.
     */
    public UUID familyId() {
        return familyId;
    }

    /**
     * 내부 account 기본키를 반환한다.
     */
    public UUID accountId() {
        return accountId;
    }

    /**
     * 저장된 refresh token SHA-256 hash를 반환한다.
     */
    public String tokenHash() {
        return tokenHash;
    }

    private static String requireHash(String value) {
        String normalized = requireText(value, "tokenHash");
        if (normalized.length() != 64) {
            throw new IllegalArgumentException("tokenHash must be a SHA-256 hex value");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
