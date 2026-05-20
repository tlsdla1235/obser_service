package com.observation.portal.domain.catalog.model;

import java.util.Objects;
import java.util.UUID;

/**
 * accepted bucket 저장 전에 확인된 application/application instance catalog 식별자다.
 */
public record ApplicationCatalogEntry(UUID applicationId, UUID applicationInstanceId) {

    /**
     * bucket foreign key로 사용할 catalog 식별자가 모두 존재하도록 보장한다.
     */
    public ApplicationCatalogEntry {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(applicationInstanceId, "applicationInstanceId must not be null");
    }
}
