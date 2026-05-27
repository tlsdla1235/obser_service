package com.observation.portal.domain.history.service;

import com.observation.portal.domain.history.model.OperationalEventType;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot-derived deterministic operational event id를 생성하는 helper다.
 *
 * <p>`markerId`를 입력으로 받지 않으며, 생성된 id는 UI key와 repeated query stability를 위한 값이지 incident workflow용
 * durable id가 아니다.</p>
 */
public final class OperationalEventIdFactory {

    private OperationalEventIdFactory() {
    }

    /**
     * `snapshot:{snapshotId}:{eventType}:{normalizedKey}` 형식의 deterministic id를 만든다.
     */
    public static String eventId(UUID snapshotId, OperationalEventType eventType, String key) {
        UUID requiredSnapshotId = Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        OperationalEventType requiredEventType = Objects.requireNonNull(eventType, "eventType must not be null");
        return "snapshot:%s:%s:%s".formatted(
                requiredSnapshotId,
                requiredEventType.value(),
                normalizeKey(key));
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        String normalized = key.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._:-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll(":_+", ":")
                .replaceAll("_+:", ":")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("normalized key must not be blank");
        }
        return normalized;
    }
}
