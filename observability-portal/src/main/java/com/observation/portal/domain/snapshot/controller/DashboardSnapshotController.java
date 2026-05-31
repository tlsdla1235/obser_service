package com.observation.portal.domain.snapshot.controller;

import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerReadModel;
import com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailService;
import com.observation.portal.domain.snapshot.service.DashboardSnapshotMarkerService;
import com.observation.portal.domain.snapshot.service.DashboardSnapshotProjectionException;
import com.observation.portal.domain.snapshot.service.InvalidSnapshotMarkerQueryException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Stored dashboard snapshot detail과 marker list API를 HTTP endpoint로 노출한다.
 *
 * <p>controller는 UUID/query 변환과 status mapping만 담당하고, account-project authorization은 interceptor에,
 * catalog path 정합성 및 stored JSON projection은 service 계층에 위임한다.</p>
 */
@RestController
@RequestMapping("/api/projects/{projectId}/applications/{applicationId}/dashboard")
public class DashboardSnapshotController {

    private final DashboardSnapshotDetailService detailService;
    private final DashboardSnapshotMarkerService markerService;

    /**
     * snapshot detail/marker read model service를 주입한다.
     */
    public DashboardSnapshotController(
            DashboardSnapshotDetailService detailService,
            DashboardSnapshotMarkerService markerService) {
        this.detailService = Objects.requireNonNull(detailService, "detailService must not be null");
        this.markerService = Objects.requireNonNull(markerService, "markerService must not be null");
    }

    /**
     * 특정 stored snapshot detail을 반환하고, invalid UUID는 400, missing/retention/path mismatch는 404로 매핑한다.
     */
    @GetMapping("/snapshots/{snapshotId}")
    public ResponseEntity<?> getSnapshotDetail(
            @PathVariable UUID projectId,
            @PathVariable UUID applicationId,
            @PathVariable String snapshotId) {
        UUID parsedSnapshotId;
        try {
            parsedSnapshotId = UUID.fromString(snapshotId);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiErrorResponse.invalidSnapshotId());
        }
        return detailService.getDetail(projectId, applicationId, parsedSnapshotId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(ApiErrorResponse.snapshotNotFoundOrExpired()));
    }

    /**
     * Stored snapshot marker list를 반환하고, empty horizon은 200 + emptyState로 표현한다.
     */
    @GetMapping("/snapshot-markers")
    public ResponseEntity<?> getSnapshotMarkers(
            @PathVariable UUID projectId,
            @PathVariable UUID applicationId,
            @RequestParam(required = false) String since,
            @RequestParam(required = false) String limit) {
        return markerService.getMarkers(projectId, applicationId, since, limit)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(ApiErrorResponse.snapshotNotFoundOrExpired()));
    }

    /**
     * marker query validation 실패를 400 Bad Request body로 매핑한다.
     */
    @ExceptionHandler(InvalidSnapshotMarkerQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidMarkerQuery() {
        return ResponseEntity.badRequest().body(ApiErrorResponse.invalidMarkerQuery());
    }

    /**
     * stored JSON projection 실패를 generic 500으로 매핑한다.
     */
    @ExceptionHandler(DashboardSnapshotProjectionException.class)
    public ResponseEntity<ApiErrorResponse> handleProjectionFailure() {
        return ResponseEntity.internalServerError().body(ApiErrorResponse.snapshotProjectionFailed());
    }

    /**
     * Snapshot API 오류 body의 stable wrapper다.
     */
    public record ApiErrorResponse(ErrorBody error) {

        static ApiErrorResponse invalidSnapshotId() {
            return new ApiErrorResponse(new ErrorBody(
                    "invalid_snapshot_id",
                    "snapshotId가 UUID 형식이 아닙니다.",
                    "snapshot marker나 dashboard link에서 다시 진입하세요."));
        }

        static ApiErrorResponse invalidMarkerQuery() {
            return new ApiErrorResponse(new ErrorBody(
                    "invalid_snapshot_marker_query",
                    "지원하지 않는 snapshot marker 조회 조건입니다.",
                    "since는 24h, 7d, 14d 중 하나를 사용하고 limit은 양의 정수로 입력하세요."));
        }

        static ApiErrorResponse snapshotNotFoundOrExpired() {
            return new ApiErrorResponse(new ErrorBody(
                    "snapshot_not_found_or_expired",
                    "저장된 snapshot detail이 없거나 보관 기간이 지나 더 이상 없습니다.",
                    "현재 상태는 application dashboard에서 다시 확인하세요."));
        }

        static ApiErrorResponse snapshotProjectionFailed() {
            return new ApiErrorResponse(new ErrorBody(
                    "snapshot_projection_failed",
                    "저장된 snapshot detail을 읽는 중 문제가 발생했습니다.",
                    "잠시 후 다시 시도하세요."));
        }
    }

    /**
     * API error code/copy/recommended action을 담는 body다.
     */
    public record ErrorBody(
            String code,
            String message,
            String recommendedAction
    ) {
    }
}
