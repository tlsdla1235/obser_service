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
 * refresh token family metadata를 저장하는 Spring Data JPA repository다.
 */
@Repository
public interface RefreshTokenFamilyJpaRepository extends JpaRepository<RefreshTokenFamilyEntity, UUID> {

    /**
     * family revoke/reuse detection과 rotation이 같은 lifecycle 상태를 보도록 row를 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select family from RefreshTokenFamilyEntity family where family.id = :id")
    Optional<RefreshTokenFamilyEntity> findByIdForUpdate(@Param("id") UUID id);
}
