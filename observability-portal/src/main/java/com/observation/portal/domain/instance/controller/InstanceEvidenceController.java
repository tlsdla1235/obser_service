package com.observation.portal.domain.instance.controller;

import com.observation.portal.domain.instance.model.InstanceEvidenceReadModel;
import com.observation.portal.domain.instance.service.InstanceEvidenceReadModelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * selected application instance의 bounded evidence read model을 HTTP endpoint로 노출한다.
 *
 * <p>controller는 UUID path variable과 404 mapping만 담당하고, membership 검증과 read model 조립은 service 계층에
 * 위임한다.</p>
 */
@RestController
@RequestMapping("/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence")
public class InstanceEvidenceController {

    private final InstanceEvidenceReadModelService instanceEvidenceReadModelService;

    /**
     * instance evidence read model 생성을 담당하는 service를 주입한다.
     */
    public InstanceEvidenceController(InstanceEvidenceReadModelService instanceEvidenceReadModelService) {
        this.instanceEvidenceReadModelService = Objects.requireNonNull(
                instanceEvidenceReadModelService,
                "instanceEvidenceReadModelService must not be null");
    }

    /**
     * project/application/instance scope가 맞는 evidence bundle을 반환하고, mismatch는 404로 매핑한다.
     */
    @GetMapping("")
    public ResponseEntity<InstanceEvidenceReadModel> getEvidence(
            @PathVariable UUID projectId,
            @PathVariable UUID applicationId,
            @PathVariable UUID instanceId) {
        return instanceEvidenceReadModelService.getEvidence(projectId, applicationId, instanceId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
