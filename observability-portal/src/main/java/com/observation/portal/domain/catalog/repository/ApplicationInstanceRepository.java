package com.observation.portal.domain.catalog.repository;

import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /**
     * Instance Evidence API path의 instance UUID가 같은 application catalog에 속하는지 검증한다.
     */
    Optional<ApplicationInstanceEntity> findByIdAndApplicationId(UUID id, UUID applicationId);

    /**
     * Application Dashboard의 Instance Detail 진입 surface에 사용할 instance entry 후보를 최신 관측순으로 조회한다.
     */
    List<ApplicationInstanceEntity> findByApplicationIdOrderByLastSeenAtDescInstanceNameAsc(
            UUID applicationId,
            Pageable pageable);

    /**
     * Snapshot writer가 bounded instance summary 후보를 50개 cap 적용 전에 전체 observed instance 기준으로 평가한다.
     */
    List<ApplicationInstanceEntity> findByApplicationId(UUID applicationId);
}
