package com.observation.starter.model.ingest;

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

    /**
     * identity tuple은 전송 전 local validation에서 모두 nonblank여야 한다.
     */
    public IngestEnvelopeIdentity {
        projectId = requireText(projectId, "projectId");
        applicationName = requireText(applicationName, "applicationName");
        environment = requireText(environment, "environment");
        instance = requireText(instance, "instance");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
