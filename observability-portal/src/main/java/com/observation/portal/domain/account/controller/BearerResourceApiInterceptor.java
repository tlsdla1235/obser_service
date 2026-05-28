package com.observation.portal.domain.account.controller;

import com.observation.portal.domain.account.service.ServiceTokenIssuer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Project resource API 앞에서 `Authorization: Bearer <access_token>` service JWT 경계를 검증한다.
 *
 * <p>인증 실패 응답에는 provider token, service token 원문, secret을 포함하지 않는다.</p>
 */
@Component
public class BearerResourceApiInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCOUNT_ID_ATTRIBUTE = "observation.portal.accountId";
    private static final String UNAUTHORIZED_BODY = """
            {"error":"unauthorized","message":"Bearer access token이 필요합니다."}
            """;

    private final ServiceTokenIssuer tokenIssuer;

    /**
     * service access token 검증을 담당하는 issuer를 주입한다.
     */
    public BearerResourceApiInterceptor(ServiceTokenIssuer tokenIssuer) {
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer, "tokenIssuer must not be null");
    }

    /**
     * resource API 요청의 Bearer header를 검증하고 유효한 account id를 request attribute에 남긴다.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Optional<String> bearerToken = bearerTokenFrom(request.getHeader(HttpHeaders.AUTHORIZATION));
        Optional<UUID> accountId = bearerToken.flatMap(tokenIssuer::verifyAccessToken);
        if (accountId.isEmpty()) {
            reject(response);
            return false;
        }
        request.setAttribute(ACCOUNT_ID_ATTRIBUTE, accountId.get());
        return true;
    }

    private static Optional<String> bearerTokenFrom(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return Optional.empty();
        }
        String value = authorization.trim();
        if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = value.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
