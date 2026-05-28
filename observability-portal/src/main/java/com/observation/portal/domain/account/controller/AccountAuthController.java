package com.observation.portal.domain.account.controller;

import com.observation.portal.domain.account.dto.AccountTokenResponse;
import com.observation.portal.domain.account.dto.AuthErrorResponse;
import com.observation.portal.domain.account.dto.GithubAuthorizeResponse;
import com.observation.portal.domain.account.dto.LogoutRequest;
import com.observation.portal.domain.account.dto.RefreshTokenRequest;
import com.observation.portal.domain.account.model.AccountAuthResult;
import com.observation.portal.domain.account.model.GithubOAuthCallbackCommand;
import com.observation.portal.domain.account.service.AccountAuthException;
import com.observation.portal.domain.account.service.AccountAuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * GitHub OAuth only account entry와 service token API를 JSON boundary로 노출한다.
 *
 * <p>cookie session, redirect fragment token 전달, email/password flow는 만들지 않는다.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AccountAuthController {

    private static final String TOKEN_ERROR_CODE = "refresh_token_invalid";
    private static final String TOKEN_ERROR_MESSAGE = "Refresh token을 사용할 수 없습니다.";

    private final AccountAuthService authService;

    /**
     * account auth 정책을 수행하는 service를 주입한다.
     */
    public AccountAuthController(AccountAuthService authService) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
    }

    /**
     * GitHub OAuth App authorization URL을 JSON으로 반환한다.
     */
    @GetMapping("/github/authorize")
    public ResponseEntity<GithubAuthorizeResponse> startGithubAuthorization() {
        return noStore(ResponseEntity.ok())
                .body(GithubAuthorizeResponse.from(authService.startGithubAuthorization()));
    }

    /**
     * GitHub OAuth callback을 완료하고 우리 서비스 access/refresh token을 JSON body로 반환한다.
     */
    @GetMapping("/github/callback")
    public ResponseEntity<AccountTokenResponse> completeGithubCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        return tokenResponse(authService.completeGithubCallback(new GithubOAuthCallbackCommand(code, state, error)));
    }

    /**
     * refresh token을 rotation하고 새 service token pair를 JSON body로 반환한다.
     */
    @PostMapping("/token/refresh")
    public ResponseEntity<AccountTokenResponse> refresh(@RequestBody(required = false) RefreshTokenRequest request) {
        return tokenResponse(authService.refresh(refreshTokenFrom(request)));
    }

    /**
     * refresh token을 revoke해 이후 rotation에 사용할 수 없게 한다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(refreshTokenFrom(request));
        return ResponseEntity.noContent().build();
    }

    /**
     * auth 실패를 provider payload나 secret 없이 일반화된 JSON error로 매핑한다.
     */
    @ExceptionHandler(AccountAuthException.class)
    public ResponseEntity<AuthErrorResponse> handleAuthException(AccountAuthException exception) {
        return noStore(ResponseEntity.badRequest())
                .body(new AuthErrorResponse(exception.errorCode(), exception.getMessage()));
    }

    /**
     * 읽을 수 없는 JSON body를 provider payload나 token 원문 없이 안전한 400 응답으로 매핑한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AuthErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return noStore(ResponseEntity.badRequest())
                .body(new AuthErrorResponse(TOKEN_ERROR_CODE, TOKEN_ERROR_MESSAGE));
    }

    private ResponseEntity<AccountTokenResponse> tokenResponse(AccountAuthResult result) {
        return noStore(ResponseEntity.ok()).body(AccountTokenResponse.from(result));
    }

    private static ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder builder) {
        return builder
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache");
    }

    private static String refreshTokenFrom(RefreshTokenRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new AccountAuthException(TOKEN_ERROR_CODE, TOKEN_ERROR_MESSAGE);
        }
        return request.refreshToken().trim();
    }

    private static String refreshTokenFrom(LogoutRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new AccountAuthException(TOKEN_ERROR_CODE, TOKEN_ERROR_MESSAGE);
        }
        return request.refreshToken().trim();
    }
}
