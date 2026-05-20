package com.observation.portal.domain.catalog.repository;

import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * `application_instances` table의 실행 인스턴스 catalog identity 조회와 저장을 담당한다.
 */
@Repository
public interface ApplicationInstanceRepository extends JpaRepository<ApplicationInstanceEntity, UUID> {

    /**
     * 한 application row 안에서 instance name으로 실행 인스턴스를 찾는다.
     */
    Optional<ApplicationInstanceEntity> findByApplicationIdAndInstanceName(UUID applicationId, String instanceName);
}
