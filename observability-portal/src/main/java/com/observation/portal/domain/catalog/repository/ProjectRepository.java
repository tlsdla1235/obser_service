package com.observation.portal.domain.catalog.repository;

import com.observation.portal.domain.catalog.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * `projects` table의 key prefix lookup을 담당하는 Spring Data JPA repository다.
 */
@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    /**
     * DB unique constraint가 보장하는 `projects.key_prefix` 단일 후보를 조회한다.
     */
    Optional<ProjectEntity> findByKeyPrefix(String keyPrefix);
}
