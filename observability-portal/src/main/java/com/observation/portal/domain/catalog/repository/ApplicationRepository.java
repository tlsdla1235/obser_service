package com.observation.portal.domain.catalog.repository;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * `applications` table의 application/environment catalog identity 조회와 저장을 담당한다.
 */
@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {

    /**
     * 한 프로젝트 안에서 application name과 environment 조합으로 catalog row를 찾는다.
     */
    Optional<ApplicationEntity> findByProjectIdAndNameAndEnvironment(UUID projectId, String name, String environment);

    /**
     * Project/Application navigation read model에서 사용할 project scope application 목록을 정렬해 조회한다.
     */
    List<ApplicationEntity> findByProjectIdOrderByNameAscEnvironmentAsc(UUID projectId);
}
