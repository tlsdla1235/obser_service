package com.observation.portal.domain.catalog.repository;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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
     * Dashboard API의 project/application path scope가 같은 catalog row를 가리키는지 확인한다.
     */
    Optional<ApplicationEntity> findByIdAndProjectId(UUID id, UUID projectId);

    /**
     * Project/Application navigation read model에서 사용할 project scope application 목록을 정렬해 조회한다.
     */
    List<ApplicationEntity> findByProjectIdOrderByNameAscEnvironmentAsc(UUID projectId);

    /**
     * UTC hourly scheduled snapshot 후보 application을 accepted bucket axis와 snapshot cutoff로만 조회한다.
     *
     * <p>heartbeat, queue backlog, worker lag는 eligibility source로 사용하지 않으며, repository는 snapshot/read model/state
     * 판단을 계산하지 않는다.</p>
     */
    @Query("select distinct application "
            + "from ApplicationEntity application "
            + "where application.status = 'active' "
            + "and exists ("
            + "  select 1 "
            + "  from AcceptedMetricBucketEntity bucket "
            + "  where bucket.applicationId = application.id "
            + "  and bucket.bucketEndUtc >= :retentionCutoffUtc"
            + "  and bucket.bucketEndUtc <= :targetWindowEndUtc"
            + "  and bucket.acceptedAt <= :snapshotCutoffAt"
            + ") "
            + "order by application.projectId asc, application.id asc")
    List<ApplicationEntity> findActiveApplicationsWithAcceptedBucketSince(
            @Param("retentionCutoffUtc") OffsetDateTime retentionCutoffUtc,
            @Param("targetWindowEndUtc") OffsetDateTime targetWindowEndUtc,
            @Param("snapshotCutoffAt") OffsetDateTime snapshotCutoffAt);
}
