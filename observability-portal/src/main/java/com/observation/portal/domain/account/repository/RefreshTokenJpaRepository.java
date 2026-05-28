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
 * hash 기반 refresh token lookup과 rotation metadata 저장을 담당하는 Spring Data JPA repository다.
 */
@Repository
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    /**
     * refresh token 원문을 hash한 값으로 token metadata를 조회한다.
     */
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * token rotation을 원자적으로 처리하기 위해 hash lookup row를 transaction 안에서 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenEntity token where token.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
