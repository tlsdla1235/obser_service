package com.observation.portal.domain.health.controller;

import com.observation.portal.domain.health.model.InternalHealthResponse;
import com.observation.portal.domain.health.service.ReadinessCheckResult;
import com.observation.portal.domain.health.service.ReadinessProbeService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 운영 배포와 reverse proxy가 사용할 내부 health HTTP boundary다.
 *
 * <p>응답은 상태 코드와 비밀 없는 component 상태만 제공하며 datasource, SSM, queue URL 같은 운영 실값은 노출하지 않는다.</p>
 */
@RestController
@RequestMapping(path = "/internal/health", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalHealthController {

    private final ReadinessProbeService readinessProbeService;

    /**
     * ready 판정에 필요한 DB/Flyway probe service를 주입한다.
     */
    public InternalHealthController(ReadinessProbeService readinessProbeService) {
        this.readinessProbeService = Objects.requireNonNull(
                readinessProbeService,
                "readinessProbeService must not be null");
    }

    /**
     * 프로세스와 HTTP server가 응답 가능한지만 확인하는 lightweight liveness endpoint다.
     */
    @GetMapping("/live")
    public ResponseEntity<InternalHealthResponse> live() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(InternalHealthResponse.live());
    }

    /**
     * 운영 요청을 받을 준비가 되었는지 DB 연결과 Flyway migration 상태로 확인한다.
     */
    @GetMapping("/ready")
    public ResponseEntity<InternalHealthResponse> ready() {
        ReadinessCheckResult result = readinessProbeService.check();
        HttpStatus status = result.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(InternalHealthResponse.ready(result));
    }
}
