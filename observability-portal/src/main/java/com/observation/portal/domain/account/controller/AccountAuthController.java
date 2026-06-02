package com.observation.portal.domain.account.controller;

import com.observation.portal.domain.account.dto.AccountTokenResponse;
import com.observation.portal.domain.account.dto.AuthErrorResponse;
import com.observation.portal.domain.account.dto.GithubAuthorizeResponse;
import com.observation.portal.domain.account.dto.GithubCallbackSessionResponse;
import com.observation.portal.domain.account.dto.GithubCallbackTokenRelayRequest;
import com.observation.portal.domain.account.dto.LogoutRequest;
import com.observation.portal.domain.account.dto.RefreshTokenRequest;
import com.observation.portal.domain.account.model.AccountAuthResult;
import com.observation.portal.domain.account.model.GithubOAuthCallbackCommand;
import com.observation.portal.domain.account.service.AccountAuthException;
import com.observation.portal.domain.account.service.AccountAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
 * GitHub OAuth only account entry, browser callback relay, service token API를 노출한다.
 *
 * <p>cookie session, redirect fragment token 전달, email/password flow는 만들지 않는다.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AccountAuthController {

    private static final String TOKEN_ERROR_CODE = "refresh_token_invalid";
    private static final String TOKEN_ERROR_MESSAGE = "Refresh token을 사용할 수 없습니다.";
    private static final String CALLBACK_RELAY_ERROR_CODE = "github_oauth_failed";
    private static final String CALLBACK_RELAY_ERROR_MESSAGE = "GitHub OAuth를 완료할 수 없습니다.";
    private static final String CALLBACK_RELAY_ENDPOINT = "/api/auth/github/callback/tokens";
    private static final String CALLBACK_MESSAGE_TYPE = "observation-portal.github-oauth-complete";
    private static final String CALLBACK_RELAY_ID_META_NAME = "observation-github-callback-relay-id";
    private static final String DASHBOARD_ENTRY_URL = "/dashboard";
    private static final String DASHBOARD_INDEX_URL = "/index.html";
    private static final String NO_REFERRER_POLICY = "no-referrer";
    private static final MediaType CALLBACK_HTML_MEDIA_TYPE =
            MediaType.parseMediaType("text/html;charset=UTF-8");

    private final AccountAuthService authService;
    private final GithubCallbackTokenRelay callbackTokenRelay;

    /**
     * account auth 정책을 수행하는 service를 주입한다.
     */
    public AccountAuthController(AccountAuthService authService) {
        this(authService, new GithubCallbackTokenRelay(java.time.Clock.systemUTC()));
    }

    /**
     * account auth 정책과 browser callback relay를 수행하는 collaborator를 주입한다.
     */
    @Autowired
    public AccountAuthController(AccountAuthService authService, GithubCallbackTokenRelay callbackTokenRelay) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.callbackTokenRelay = Objects.requireNonNull(
                callbackTokenRelay,
                "callbackTokenRelay must not be null");
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
     * GitHub OAuth callback을 완료하고 browser-facing HTML relay page를 반환한다.
     *
     * <p>기본 callback URL은 token JSON을 화면에 렌더링하지 않는다. Dashboard popup opener가 1회용 relay를
     * 호출해 service access token만 메모리 상태로 받는다.</p>
     */
    @GetMapping(value = "/github/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> completeGithubCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        AccountAuthResult result = authService.completeGithubCallback(new GithubOAuthCallbackCommand(code, state, error));
        GithubCallbackTokenRelay.RelayTicket relayTicket =
                callbackTokenRelay.store(GithubCallbackSessionResponse.from(result));
        return noStore(ResponseEntity.ok()
                .contentType(CALLBACK_HTML_MEDIA_TYPE))
                .body(callbackRelayPage(relayTicket));
    }

    /**
     * 브라우저 callback page가 dashboard opener로 넘길 service access token relay를 1회 회수한다.
     */
    @PostMapping("/github/callback/tokens")
    public ResponseEntity<GithubCallbackSessionResponse> consumeGithubCallbackRelay(
            @RequestBody(required = false) GithubCallbackTokenRelayRequest request) {
        if (request == null || request.relayId() == null || request.relayId().isBlank()) {
            throw new AccountAuthException(CALLBACK_RELAY_ERROR_CODE, CALLBACK_RELAY_ERROR_MESSAGE);
        }
        return noStore(ResponseEntity.ok()).body(callbackTokenRelay.consume(request.relayId()));
    }

    /**
     * 테스트/도구용 GitHub OAuth callback endpoint다.
     *
     * <p>브라우저 기본 callback과 분리해 raw token pair JSON이 dashboard 화면에 그대로 렌더링되지 않게 한다.</p>
     */
    @GetMapping("/github/callback/token")
    public ResponseEntity<AccountTokenResponse> completeGithubCallbackToken(
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

    private static String callbackRelayPage(GithubCallbackTokenRelay.RelayTicket relayTicket) {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta http-equiv="Cache-Control" content="no-store">
                  <meta name="%s" content="%s">
                  <title>GitHub 로그인 완료</title>
                </head>
                <body>
                  <main>
                    <h1>GitHub 로그인 완료</h1>
                    <p id="callback-status" aria-live="polite">Dashboard로 인증 결과를 전달하는 중입니다.</p>
                  </main>
                  <script>
                    (() => {
                      const relayId = "%s";
                      const relayEndpoint = "%s";
                      const messageType = "%s";
                      const dashboardEntryUrl = "%s";
                      const dashboardIndexUrl = "%s";
                      const status = document.querySelector('#callback-status');
                      const setStatus = (message) => {
                        if (status) {
                          status.textContent = message;
                        }
                      };
                      if (!window.opener || window.opener.closed) {
                        completeInCurrentWindow();
                        return;
                      }
                      window.opener.postMessage({ type: messageType, relayId }, window.location.origin);
                      setStatus('Dashboard로 돌아가 주세요.');
                      setTimeout(() => window.close(), 100);
                      function completeInCurrentWindow() {
                        fetch(relayEndpoint, {
                          method: 'POST',
                          cache: 'no-store',
                          referrerPolicy: 'no-referrer',
                          headers: {
                            'Accept': 'application/json',
                            'Content-Type': 'application/json'
                          },
                          body: JSON.stringify({ relayId })
                        })
                          .then((response) => {
                            if (!response.ok) {
                              throw new Error('callback_relay_failed');
                            }
                            return response.json();
                          })
                          .then((token) => {
                            const accessToken = String(token && token.accessToken || '').trim();
                            if (!accessToken) {
                              throw new Error('callback_relay_malformed');
                            }
                            return fetch(dashboardIndexUrl, {
                              cache: 'no-store',
                              referrerPolicy: 'no-referrer'
                            }).then((response) => {
                              if (!response.ok) {
                                throw new Error('dashboard_load_failed');
                              }
                              return response.text();
                            }).then((dashboardHtml) => ({ accessToken, dashboardHtml }));
                          })
                          .then(({ accessToken, dashboardHtml }) => {
                            document.open();
                            document.write(dashboardHtml);
                            document.close();
                            const applyToken = () => {
                              if (window.observationPortalAuth) {
                                window.history.replaceState(null, '', dashboardEntryUrl);
                                window.observationPortalAuth.setAccessToken(accessToken);
                                return;
                              }
                              window.setTimeout(applyToken, 25);
                            };
                            applyToken();
                          })
                          .catch(() => {
                            setStatus('GitHub 로그인을 완료할 수 없습니다. 이 창을 닫고 다시 시도해 주세요.');
                          });
                      }
                    })();
                  </script>
                </body>
                </html>
                """.formatted(
                CALLBACK_RELAY_ID_META_NAME,
                relayTicket.relayId(),
                relayTicket.relayId(),
                CALLBACK_RELAY_ENDPOINT,
                CALLBACK_MESSAGE_TYPE,
                DASHBOARD_ENTRY_URL,
                DASHBOARD_INDEX_URL);
    }

    private static ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder builder) {
        return builder
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("Referrer-Policy", NO_REFERRER_POLICY);
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
