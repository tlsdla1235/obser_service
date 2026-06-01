package com.observation.portal.domain.catalog.controller;

import com.observation.portal.domain.account.controller.BearerResourceApiInterceptor;
import com.observation.portal.domain.catalog.dto.ProjectRegistrationErrorResponse;
import com.observation.portal.domain.catalog.dto.ProjectRegistrationRequest;
import com.observation.portal.domain.catalog.dto.ProjectRegistrationResponse;
import com.observation.portal.domain.catalog.model.ProjectRegistrationCommand;
import com.observation.portal.domain.catalog.model.ProjectRegistrationResult;
import com.observation.portal.domain.catalog.service.ProjectRegistrationException;
import com.observation.portal.domain.catalog.service.ProjectRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/**
 * authenticated public onboarding project registration API를 HTTP boundary로 노출한다.
 *
 * <p>Bearer interceptor가 검증한 account id와 request의 non-secret project name만 service에 넘기고, 성공 시 raw
 * starter credential은 1회 표시 response로만 반환한다.</p>
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectRegistrationController {

    private final ProjectRegistrationService registrationService;

    /**
     * project registration use case service를 주입한다.
     */
    public ProjectRegistrationController(ProjectRegistrationService registrationService) {
        this.registrationService = Objects.requireNonNull(
                registrationService,
                "registrationService must not be null");
    }

    /**
     * 현재 account 기준 project와 active member membership을 만들고 starter credential을 1회 표시로 반환한다.
     */
    @PostMapping
    public ResponseEntity<?> registerProject(
            HttpServletRequest servletRequest,
            @RequestBody(required = false) ProjectRegistrationRequest request) {
        UUID accountId = BearerResourceApiInterceptor.requiredAccountId(servletRequest);
        try {
            String projectName = request == null ? null : request.name();
            ProjectRegistrationResult result = registrationService.register(
                    new ProjectRegistrationCommand(accountId, projectName));
            return ResponseEntity.created(URI.create("/api/projects/%s".formatted(result.projectId())))
                    .headers(ProjectRegistrationController::secretResponseHeaders)
                    .body(ProjectRegistrationResponse.from(result));
        } catch (IllegalArgumentException exception) {
            return registrationError(ProjectRegistrationException.invalidName());
        } catch (ProjectRegistrationException exception) {
            return registrationError(exception);
        }
    }

    private static ResponseEntity<ProjectRegistrationErrorResponse> registrationError(
            ProjectRegistrationException exception) {
        HttpStatus status = exception.reason() == ProjectRegistrationException.Reason.DUPLICATE_NAME
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .headers(ProjectRegistrationController::secretResponseHeaders)
                .body(new ProjectRegistrationErrorResponse(exception.errorCode(), exception.responseMessage()));
    }

    private static void secretResponseHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
    }

    /**
     * JSON body 파싱 실패를 request body 원문 없이 안전한 registration 오류로 수렴시킨다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProjectRegistrationErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception) {
        return registrationError(ProjectRegistrationException.invalidName());
    }
}
