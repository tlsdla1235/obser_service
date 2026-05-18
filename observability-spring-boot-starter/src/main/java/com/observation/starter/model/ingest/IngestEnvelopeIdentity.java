package com.observation.starter.model.ingest;

import java.util.regex.Pattern;

/**
 * starter local configuration에서 온 project/application/environment/instance identity다.
 *
 * <p>idempotency key와 envelope application block을 만들 때 사용하며, builder가 포털에 identity
 * lookup을 요청하지 않도록 필요한 값을 모두 로컬 입력으로 고정한다.</p>
 */
public record IngestEnvelopeIdentity(
        String projectId,
        String applicationName,
        String environment,
        String instance
) {

    private static final Pattern HEADER_SAFE_COMPONENT = Pattern.compile("[A-Za-z0-9._-]+");

    /**
     * identity tuple은 전송 전 local validation에서 모두 nonblank이고 header/idempotency component로 안전해야 한다.
     */
    public IngestEnvelopeIdentity {
        projectId = requireHeaderSafeComponent(projectId, "projectId");
        applicationName = requireHeaderSafeComponent(applicationName, "applicationName");
        environment = requireHeaderSafeComponent(environment, "environment");
        instance = requireHeaderSafeComponent(instance, "instance");
    }

    private static String requireHeaderSafeComponent(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String trimmed = value.trim();
        if (!HEADER_SAFE_COMPONENT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    name + " must contain only letters, digits, '.', '_' or '-'");
        }
        return trimmed;
    }
}
