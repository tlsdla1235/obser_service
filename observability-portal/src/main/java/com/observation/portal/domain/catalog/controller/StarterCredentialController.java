package com.observation.portal.domain.catalog.controller;

import com.observation.portal.domain.catalog.dto.StarterCredentialMetadataResponse;
import com.observation.portal.domain.catalog.dto.StarterCredentialRotationResponse;
import com.observation.portal.domain.catalog.service.StarterCredentialLifecycleException;
import com.observation.portal.domain.catalog.service.StarterCredentialService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * project-scoped starter credential lifecycle API의 HTTP boundary다.
 *
 * <p>Bearer 인증과 account-project membership 검증은 MVC interceptor에서 먼저 수행되고, 이 controller는 service 결과를
 * raw 노출 허용 범위에 맞는 DTO로만 변환한다.</p>
 */
@RestController
@RequestMapping("/api/projects/{projectId}/starter-credential")
public class StarterCredentialController {

    private final StarterCredentialService credentialService;

    /**
     * starter credential lifecycle service를 주입한다.
     */
    public StarterCredentialController(StarterCredentialService credentialService) {
        this.credentialService = Objects.requireNonNull(
                credentialService,
                "credentialService must not be null");
    }

    /**
     * raw value/hash 없이 starter credential metadata만 조회한다.
     */
    @GetMapping
    public ResponseEntity<StarterCredentialMetadataResponse> metadata(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok()
                    .headers(StarterCredentialController::metadataResponseHeaders)
                    .body(StarterCredentialMetadataResponse.from(credentialService.metadata(projectId)));
        } catch (StarterCredentialLifecycleException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 새 starter credential을 발급하고 raw display value를 1회 표시 response로만 반환한다.
     */
    @PostMapping("/rotations")
    public ResponseEntity<StarterCredentialRotationResponse> rotate(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok()
                    .headers(StarterCredentialController::secretResponseHeaders)
                    .body(StarterCredentialRotationResponse.from(credentialService.rotate(projectId)));
        } catch (StarterCredentialLifecycleException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * starter credential을 revoked 상태로 바꾸고 raw value 없이 metadata만 반환한다.
     */
    @PostMapping("/revocations")
    public ResponseEntity<StarterCredentialMetadataResponse> revoke(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok()
                    .headers(StarterCredentialController::metadataResponseHeaders)
                    .body(StarterCredentialMetadataResponse.from(credentialService.revoke(projectId)));
        } catch (StarterCredentialLifecycleException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    private static void secretResponseHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
    }

    private static void metadataResponseHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
