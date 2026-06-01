package com.observation.portal.domain.admin.service;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * local smoke seed command가 읽는 설정 묶음이다.
 *
 * <p>raw project key는 local-only 설정 source에서 읽기 위한 입력으로만 보관하고, result/log 문자열에는 싣지 않는다.</p>
 */
public record SmokeProjectSeedProperties(
        boolean enabled,
        Set<String> activeProfiles,
        String accountProviderSubject,
        String accountDisplayName,
        String projectName,
        String rawProjectKey,
        UUID projectId) {

    private static final String PREFIX = "portal.smoke.seed.";

    /**
     * Spring Environment에서 `portal.smoke.seed.*` 설정을 읽어 seed 입력으로 정규화한다.
     */
    public static SmokeProjectSeedProperties from(Environment environment) {
        Environment requiredEnvironment = Objects.requireNonNull(environment, "environment must not be null");
        return new SmokeProjectSeedProperties(
                requiredEnvironment.getProperty(PREFIX + "enabled", Boolean.class, false),
                Arrays.stream(requiredEnvironment.getActiveProfiles()).collect(Collectors.toUnmodifiableSet()),
                requiredEnvironment.getProperty(PREFIX + "account-provider-subject"),
                requiredEnvironment.getProperty(PREFIX + "account-display-name"),
                requiredEnvironment.getProperty(PREFIX + "project-name"),
                requiredEnvironment.getProperty(PREFIX + "raw-project-key"),
                optionalUuid(requiredEnvironment.getProperty(PREFIX + "project-id")));
    }

    /**
     * caller가 null set을 넘겨도 service가 안전하게 profile guard를 평가할 수 있도록 보정한다.
     */
    public SmokeProjectSeedProperties {
        activeProfiles = activeProfiles == null ? Set.of() : Set.copyOf(activeProfiles);
        accountProviderSubject = trimToNull(accountProviderSubject);
        accountDisplayName = trimToNull(accountDisplayName);
        projectName = trimToNull(projectName);
        rawProjectKey = trimToNull(rawProjectKey);
    }

    /**
     * optional stable project id가 없으면 service가 새 UUID를 생성하도록 null을 반환한다.
     */
    private static UUID optionalUuid(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException exception) {
            throw new SmokeProjectSeedException("portal.smoke.seed.project-id must be a UUID when configured");
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
