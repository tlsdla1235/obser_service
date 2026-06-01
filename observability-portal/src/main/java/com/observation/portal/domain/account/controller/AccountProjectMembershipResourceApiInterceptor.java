package com.observation.portal.domain.account.controller;

import com.observation.portal.domain.account.service.AccountProjectMembershipService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * `/api/projects/{projectId}/applications/**` resource API 앞에서 account-project membership을 fail-closed로 검증한다.
 *
 * <p>Bearer 검증이 남긴 account id와 path project id만 사용하며, membership mismatch는 body 없는 404로 수렴시킨다.</p>
 */
@Component
public class AccountProjectMembershipResourceApiInterceptor implements HandlerInterceptor {

    private static final String PROJECT_ID_PATH_VARIABLE = "projectId";

    private final AccountProjectMembershipService membershipService;

    /**
     * account-project active membership 판정을 담당하는 service를 주입한다.
     */
    public AccountProjectMembershipResourceApiInterceptor(AccountProjectMembershipService membershipService) {
        this.membershipService = Objects.requireNonNull(membershipService, "membershipService must not be null");
    }

    /**
     * project-scoped resource service가 catalog lookup이나 query validation을 수행하기 전에 membership을 확인한다.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID accountId = BearerResourceApiInterceptor.requiredAccountId(request);
        Optional<UUID> projectId = projectIdFrom(request);
        if (projectId.isEmpty() || !membershipService.hasActiveMembership(accountId, projectId.orElseThrow())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            return false;
        }
        return true;
    }

    private static Optional<UUID> projectIdFrom(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(variables instanceof Map<?, ?> pathVariables)) {
            return Optional.empty();
        }
        Object rawProjectId = pathVariables.get(PROJECT_ID_PATH_VARIABLE);
        if (rawProjectId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(String.valueOf(rawProjectId)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
