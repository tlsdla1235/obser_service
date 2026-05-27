package com.observation.portal.domain.history.controller;

import com.observation.portal.domain.history.service.InvalidOperationalEventHistoryQueryException;
import com.observation.portal.domain.history.service.OperationalEventHistoryProjectionException;
import com.observation.portal.domain.history.service.OperationalEventHistoryService;
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
 * Operational event history API를 `/operational-events` HTTP endpoint로 노출한다.
 *
 * <p>controller는 path/query 변환과 HTTP status mapping만 담당하고, membership 검증과 stored snapshot source 조회는
 * service에 위임한다.</p>
 */
@RestController
@RequestMapping("/api/projects/{projectId}/applications/{applicationId}")
public class OperationalEventHistoryController {

    private final OperationalEventHistoryService historyService;

    /**
     * operational event history service를 주입한다.
     */
    public OperationalEventHistoryController(OperationalEventHistoryService historyService) {
        this.historyService = Objects.requireNonNull(historyService, "historyService must not be null");
    }

    /**
     * Stored dashboard snapshot source 기반 compact operational event history를 반환한다.
     */
    @GetMapping("/operational-events")
    public ResponseEntity<?> getOperationalEvents(
            @PathVariable UUID projectId,
            @PathVariable UUID applicationId,
            @RequestParam(required = false) String since,
            @RequestParam(required = false) String limit) {
        return historyService.getHistory(projectId, applicationId, since, limit)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(ApiErrorResponse.notFound()));
    }

    /**
     * since/limit validation 실패를 400 Bad Request body로 매핑한다.
     */
    @ExceptionHandler(InvalidOperationalEventHistoryQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidQuery() {
        return ResponseEntity.badRequest().body(ApiErrorResponse.invalidQuery());
    }

    /**
     * stored snapshot source row 조회나 projection 실패를 generic 500으로 매핑한다.
     */
    @ExceptionHandler(OperationalEventHistoryProjectionException.class)
    public ResponseEntity<ApiErrorResponse> handleProjectionFailure() {
        return ResponseEntity.internalServerError().body(ApiErrorResponse.projectionFailed());
    }

    /**
     * Operational event history API 오류 body의 stable wrapper다.
     */
    public record ApiErrorResponse(ErrorBody error) {

        static ApiErrorResponse invalidQuery() {
            return new ApiErrorResponse(new ErrorBody(
                    "invalid_operational_event_history_query",
                    "지원하지 않는 operational event history 조회 조건입니다.",
                    "since는 양의 정수와 h 또는 d 단위를 사용하고 limit은 양의 정수로 입력하세요."));
        }

        static ApiErrorResponse notFound() {
            return new ApiErrorResponse(new ErrorBody(
                    "operational_event_history_not_found",
                    "프로젝트 또는 애플리케이션을 찾을 수 없습니다.",
                    "project/application 선택을 다시 확인하세요."));
        }

        static ApiErrorResponse projectionFailed() {
            return new ApiErrorResponse(new ErrorBody(
                    "operational_event_history_projection_failed",
                    "저장된 dashboard snapshot source를 읽는 중 문제가 발생했습니다.",
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
