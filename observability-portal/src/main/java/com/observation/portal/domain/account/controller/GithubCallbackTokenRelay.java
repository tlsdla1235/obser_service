package com.observation.portal.domain.account.controller;

import com.observation.portal.domain.account.dto.GithubCallbackSessionResponse;
import com.observation.portal.domain.account.service.AccountAuthException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * GitHub OAuth browser callback 결과를 dashboard opener가 한 번만 회수할 수 있게 보관하는 짧은 수명 relay다.
 *
 * <p>service token을 URL, cookie, browser storage, callback HTML body에 넣지 않기 위해 server memory에 access token
 * 응답만 임시 보관한다. Refresh token은 기본 dashboard 흐름에 보관하지 않는다.</p>
 */
@Component
public class GithubCallbackTokenRelay {

    private static final Duration RELAY_TTL = Duration.ofMinutes(2);
    private static final String GENERIC_RELAY_FAILURE = "GitHub OAuth를 완료할 수 없습니다.";
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<String, RelayEntry> entries = new ConcurrentHashMap<>();

    /**
     * relay 만료 판단을 테스트 가능하게 하기 위해 UTC clock을 주입받는다.
     */
    public GithubCallbackTokenRelay(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
    }

    /**
     * access token 응답을 짧은 수명 relay에 저장하고 callback HTML에 넣을 1회용 id를 반환한다.
     */
    public RelayTicket store(GithubCallbackSessionResponse response) {
        Objects.requireNonNull(response, "response must not be null");
        OffsetDateTime now = nowUtc();
        purgeExpired(now);
        String relayId = newRelayId();
        OffsetDateTime expiresAt = now.plus(RELAY_TTL);
        entries.put(relayId, new RelayEntry(response, expiresAt));
        return new RelayTicket(relayId, expiresAt);
    }

    /**
     * relay id에 대응하는 access token 응답을 한 번만 반환한다.
     *
     * <p>없는 id, 재사용된 id, 만료된 id 모두 원문 id를 echo하지 않는 일반화된 auth 실패로 닫는다.</p>
     */
    public GithubCallbackSessionResponse consume(String relayId) {
        String normalizedRelayId = requireRelayId(relayId);
        RelayEntry entry = entries.remove(normalizedRelayId);
        OffsetDateTime now = nowUtc();
        if (entry == null || !entry.isActiveAt(now)) {
            throw new AccountAuthException("github_oauth_failed", GENERIC_RELAY_FAILURE);
        }
        return entry.response();
    }

    private void purgeExpired(OffsetDateTime now) {
        entries.entrySet().removeIf(entry -> !entry.getValue().isActiveAt(now));
    }

    private String newRelayId() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return BASE64_URL.encodeToString(randomBytes);
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String requireRelayId(String relayId) {
        if (relayId == null || relayId.isBlank()) {
            throw new AccountAuthException("github_oauth_failed", GENERIC_RELAY_FAILURE);
        }
        return relayId.trim();
    }

    /**
     * callback HTML이 relay 회수 요청에 사용할 1회용 id와 만료 시각이다.
     */
    public record RelayTicket(String relayId, OffsetDateTime expiresAt) {

        public RelayTicket {
            if (relayId == null || relayId.isBlank()) {
                throw new IllegalArgumentException("relayId must not be blank");
            }
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }

    private record RelayEntry(GithubCallbackSessionResponse response, OffsetDateTime expiresAt) {

        private RelayEntry {
            Objects.requireNonNull(response, "response must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }

        private boolean isActiveAt(OffsetDateTime now) {
            return expiresAt.isAfter(now);
        }
    }
}
