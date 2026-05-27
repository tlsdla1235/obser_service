package com.observation.portal.domain.history.model;

/**
 * Operational event item이 포함할 수 있는 bounded evidence field다.
 *
 * <p>source는 stored snapshot/read model과 5.8-b handoff field로 제한하며 raw snapshot JSON, raw bucket,
 * endpoint p95/p99, trace/per-request/query field를 담지 않는다.</p>
 */
public record OperationalEventEvidence(
        String ruleId,
        String endpointKey,
        String method,
        String route,
        String snapshotDetailAnchor,
        String anchorStatus
) {

    /**
     * optional evidence field는 비어 있으면 null로 정규화한다.
     */
    public OperationalEventEvidence {
        ruleId = trimNullable(ruleId);
        endpointKey = trimNullable(endpointKey);
        method = trimNullable(method);
        route = trimNullable(route);
        snapshotDetailAnchor = trimNullable(snapshotDetailAnchor);
        anchorStatus = trimNullable(anchorStatus);
    }

    private static String trimNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
