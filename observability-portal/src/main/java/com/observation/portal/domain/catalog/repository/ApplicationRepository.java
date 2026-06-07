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
     * UTC hourly scheduled snapshot 후보 application을 accepted bucket과 최근 starter heartbeat 조건으로 조회한다.
     *
     * <p>heartbeat는 snapshot 저장 가능 여부만 제한하며, read model/state 계산이나 metric freshness source로 합성하지 않는다.</p>
     */
    @Query(value = """
            select distinct app.*
            from applications app
            where app.status = 'active'
              and exists (
                select 1
                from accepted_metric_buckets bucket
                where bucket.application_id = app.id
                  and bucket.bucket_end_utc >= :retentionCutoffUtc
                  and bucket.bucket_end_utc <= :targetWindowEndUtc
                  and bucket.accepted_at <= :snapshotCutoffAt
              )
              and exists (
                select 1
                from starter_heartbeat_telemetry heartbeat
                where heartbeat.project_id = app.project_id
                  and heartbeat.application_name = app.name
                  and heartbeat.environment = app.environment
                  and heartbeat.last_received_at_utc <= cast(:heartbeatFreshnessReferenceUtc as timestamptz)
                  and heartbeat.last_received_at_utc >= (
                    cast(:heartbeatFreshnessReferenceUtc as timestamptz)
                      - (greatest(90, heartbeat.interval_seconds * 3) * interval '1 second')
                  )
              )
            order by app.project_id asc, app.id asc
            """, nativeQuery = true)
    List<ApplicationEntity> findActiveApplicationsEligibleForScheduledSnapshot(
            @Param("retentionCutoffUtc") OffsetDateTime retentionCutoffUtc,
            @Param("targetWindowEndUtc") OffsetDateTime targetWindowEndUtc,
            @Param("snapshotCutoffAt") OffsetDateTime snapshotCutoffAt,
            @Param("heartbeatFreshnessReferenceUtc") OffsetDateTime heartbeatFreshnessReferenceUtc);
}
