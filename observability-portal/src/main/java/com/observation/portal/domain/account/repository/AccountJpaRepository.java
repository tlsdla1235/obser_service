package com.observation.portal.domain.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * internal account row 저장을 담당하는 Spring Data JPA repository다.
 */
@Repository
public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {
}
