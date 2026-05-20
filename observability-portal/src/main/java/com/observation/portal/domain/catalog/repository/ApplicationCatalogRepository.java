package com.observation.portal.domain.catalog.repository;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.model.ApplicationCatalogEntry;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * accepted ingest 시 application과 instance catalog row를 찾거나 생성하는 repository layer 구성요소다.
 *
 * <p>public onboarding API가 아니라 persistence path의 identity maintenance 용도로만 사용한다.</p>
 */
@Repository
public class ApplicationCatalogRepository {

    private static final String ACTIVE_STATUS = "active";

    private final ApplicationRepository applicationRepository;
    private final ApplicationInstanceRepository applicationInstanceRepository;

    /**
     * catalog identity 조회에 필요한 Spring Data repository들을 주입한다.
     */
    public ApplicationCatalogRepository(
            ApplicationRepository applicationRepository,
            ApplicationInstanceRepository applicationInstanceRepository) {
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.applicationInstanceRepository = Objects.requireNonNull(
                applicationInstanceRepository,
                "applicationInstanceRepository must not be null");
    }

    /**
     * application/environment와 instance name을 기준으로 catalog row를 찾거나 만들고 last-seen 시각을 갱신한다.
     */
    public ApplicationCatalogEntry getOrCreate(
            UUID projectId,
            String applicationName,
            String environment,
            String instanceName,
            OffsetDateTime seenAt) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        OffsetDateTime requiredSeenAt = Objects.requireNonNull(seenAt, "seenAt must not be null");

        ApplicationEntity application = applicationRepository
                .findByProjectIdAndNameAndEnvironment(requiredProjectId, applicationName, environment)
                .orElseGet(() -> new ApplicationEntity(
                        UUID.randomUUID(),
                        requiredProjectId,
                        applicationName,
                        environment,
                        ACTIVE_STATUS,
                        requiredSeenAt,
                        requiredSeenAt,
                        requiredSeenAt,
                        requiredSeenAt));
        application.markSeenAt(requiredSeenAt);
        ApplicationEntity savedApplication = applicationRepository.save(application);

        ApplicationInstanceEntity instance = applicationInstanceRepository
                .findByApplicationIdAndInstanceName(savedApplication.id(), instanceName)
                .orElseGet(() -> new ApplicationInstanceEntity(
                        UUID.randomUUID(),
                        savedApplication.id(),
                        instanceName,
                        requiredSeenAt,
                        requiredSeenAt,
                        requiredSeenAt,
                        requiredSeenAt));
        instance.markSeenAt(requiredSeenAt);
        ApplicationInstanceEntity savedInstance = applicationInstanceRepository.save(instance);

        return new ApplicationCatalogEntry(savedApplication.id(), savedInstance.id());
    }
}
