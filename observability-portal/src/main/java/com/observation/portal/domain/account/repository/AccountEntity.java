package com.observation.portal.domain.account.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 내부 account row를 나타내는 JPA entity다.
 *
 * <p>외부 identity와 profile metadata는 `external_identities`에 분리하고, 이 entity는 account lifecycle만 저장한다.</p>
 */
@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected AccountEntity() {
    }

    private AccountEntity(UUID id, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.status = requireText(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * GitHub provider subject 검증이 끝난 뒤 활성 account row를 만든다.
     */
    public static AccountEntity active(UUID id, OffsetDateTime now) {
        return new AccountEntity(id, "active", now, now);
    }

    /**
     * account 기본키를 반환한다.
     */
    public UUID id() {
        return id;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
