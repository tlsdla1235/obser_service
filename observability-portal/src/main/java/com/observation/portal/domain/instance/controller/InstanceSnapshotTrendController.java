package com.observation.portal.domain.instance.controller;

import com.observation.portal.domain.instance.model.InstanceSnapshotTrendReadModel;
import com.observation.portal.domain.instance.service.InstanceSnapshotTrendService;
import com.observation.portal.domain.instance.service.InvalidSnapshotTrendQueryException;
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
 * selected application instance의 stored snapshot trend projection을 HTTP endpoint로 노출한다.
 *
 * <p>controller는 UUID path variable, optional `since`/`limit` query parameter 전달, HTTP status mapping만 담당하고
 * membership 검증과 snapshot JSON projection은 service 계층에 위임한다.</p>
 */
@RestController
@RequestMapping("/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend")
public class InstanceSnapshotTrendController {

    private final InstanceSnapshotTrendService instanceSnapshotTrendService;

    /**
     * instance snapshot trend read model 생성을 담당하는 service를 주입한다.
     */
    public InstanceSnapshotTrendController(InstanceSnapshotTrendService instanceSnapshotTrendService) {
        this.instanceSnapshotTrendService = Objects.requireNonNull(
                instanceSnapshotTrendService,
                "instanceSnapshotTrendService must not be null");
    }

    /**
     * project/application/instance scope가 맞는 stored snapshot trend를 반환하고, membership mismatch는 404로 매핑한다.
     */
    @GetMapping("")
    public ResponseEntity<InstanceSnapshotTrendReadModel> getSnapshotTrend(
            @PathVariable UUID projectId,
            @PathVariable UUID applicationId,
            @PathVariable UUID instanceId,
            @RequestParam(required = false) String since,
            @RequestParam(required = false) String limit) {
        return instanceSnapshotTrendService.getTrend(projectId, applicationId, instanceId, since, limit)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Story 5.7 query token/limit validation 실패를 400 Bad Request로 매핑한다.
     */
    @ExceptionHandler(InvalidSnapshotTrendQueryException.class)
    public ResponseEntity<Void> handleInvalidSnapshotTrendQuery() {
        return ResponseEntity.badRequest().build();
    }
}
