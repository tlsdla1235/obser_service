package com.observation.portal.domain.account.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * OAuth state nonce hash 저장과 callback 1회 소비를 담당하는 Spring Data JPA repository다.
 */
@Repository
public interface OAuthStateNonceJpaRepository extends JpaRepository<OAuthStateNonceEntity, UUID> {

    /**
     * callback state 소비를 원자적으로 처리하기 위해 nonce hash row를 transaction 안에서 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select nonce from OAuthStateNonceEntity nonce where nonce.nonceHash = :nonceHash")
    Optional<OAuthStateNonceEntity> findByNonceHashForUpdate(@Param("nonceHash") String nonceHash);

    /**
     * 테스트와 감사성 확인에서 raw state 없이 nonce hash row를 조회한다.
     */
    Optional<OAuthStateNonceEntity> findByNonceHash(String nonceHash);
}
