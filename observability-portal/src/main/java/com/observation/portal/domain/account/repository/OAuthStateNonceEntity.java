package com.observation.portal.domain.account.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * OAuth state nonce 원문 대신 hash와 1회성 사용 상태를 저장하는 JPA entity다.
 */
@Entity
@Table(name = "oauth_state_nonces")
public class OAuthStateNonceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "nonce_hash", nullable = false, length = 64)
    private String nonceHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected OAuthStateNonceEntity() {
    }

    private OAuthStateNonceEntity(UUID id, String nonceHash, String status, OffsetDateTime expiresAt, OffsetDateTime now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.nonceHash = requireHash(nonceHash);
        this.status = requireText(status, "status");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * GitHub authorize 시작 시 저장할 active state nonce hash row를 만든다.
     */
    public static OAuthStateNonceEntity active(UUID id, String nonceHash, OffsetDateTime expiresAt, OffsetDateTime now) {
        return new OAuthStateNonceEntity(id, nonceHash, "active", expiresAt, now);
    }

    /**
     * 현재 시각 기준으로 callback에서 소비 가능한 nonce인지 확인한다.
     */
    public boolean isActiveAt(OffsetDateTime now) {
        return "active".equals(status) && consumedAt == null && expiresAt.isAfter(now);
    }

    /**
     * OAuth callback에서 state nonce를 1회 사용 처리한다.
     */
    public void markConsumed(OffsetDateTime now) {
        this.status = "consumed";
        this.consumedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * 저장된 nonce SHA-256 hash를 반환한다.
     */
    public String nonceHash() {
        return nonceHash;
    }

    private static String requireHash(String value) {
        String normalized = requireText(value, "nonceHash");
        if (normalized.length() != 64) {
            throw new IllegalArgumentException("nonceHash must be a SHA-256 hex value");
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
