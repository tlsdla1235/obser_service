package com.observation.portal.domain.account.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * GitHub provider identity와 내부 account의 연결을 저장하는 JPA entity다.
 *
 * <p>providerSubject는 stable key이며 email/display/avatar는 교체 가능한 profile metadata로만 취급한다.</p>
 */
@Entity
@Table(name = "external_identities")
public class ExternalIdentityEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_subject", nullable = false, length = 160)
    private String providerSubject;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected ExternalIdentityEntity() {
    }

    private ExternalIdentityEntity(
            UUID id,
            UUID accountId,
            String provider,
            String providerSubject,
            String email,
            String displayName,
            String avatarUrl,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.provider = requireText(provider, "provider");
        this.providerSubject = requireText(providerSubject, "providerSubject");
        this.email = trimToNull(email);
        this.displayName = trimToNull(displayName);
        this.avatarUrl = trimToNull(avatarUrl);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * 검증된 GitHub identity를 내부 account에 연결하는 row를 만든다.
     */
    public static ExternalIdentityEntity create(
            UUID id,
            UUID accountId,
            String provider,
            String providerSubject,
            String email,
            String displayName,
            String avatarUrl,
            OffsetDateTime now) {
        return new ExternalIdentityEntity(
                id,
                accountId,
                provider,
                providerSubject,
                email,
                displayName,
                avatarUrl,
                now,
                now);
    }

    /**
     * provider subject는 유지하고 GitHub profile metadata만 최신 값으로 갱신한다.
     */
    public void updateProfile(String email, String displayName, String avatarUrl, OffsetDateTime now) {
        this.email = trimToNull(email);
        this.displayName = trimToNull(displayName);
        this.avatarUrl = trimToNull(avatarUrl);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * 내부 account 기본키를 반환한다.
     */
    public UUID accountId() {
        return accountId;
    }

    /**
     * OAuth provider 이름을 반환한다.
     */
    public String provider() {
        return provider;
    }

    /**
     * GitHub user id 또는 provider subject stable key를 반환한다.
     */
    public String providerSubject() {
        return providerSubject;
    }

    /**
     * profile metadata인 email을 반환한다.
     */
    public String email() {
        return email;
    }

    /**
     * GitHub profile metadata display name을 반환한다.
     */
    public String displayName() {
        return displayName;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
