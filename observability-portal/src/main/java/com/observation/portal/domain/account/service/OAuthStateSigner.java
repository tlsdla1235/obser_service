package com.observation.portal.domain.account.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * server session 없이 OAuth callback state를 검증하기 위한 signed expiring state를 만든다.
 *
 * <p>state payload는 만료 시각과 난수 nonce만 담고, provider token이나 secret은 포함하지 않는다.</p>
 */
@Component
public class OAuthStateSigner {

    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final Clock clock;
    private final byte[] signingKey;
    private final Duration ttl;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * state 서명에 사용할 clock, signing key, 만료 시간을 주입한다.
     */
    public OAuthStateSigner(
            Clock clock,
            @Value("${portal.auth.oauth-state.signing-key:${portal.auth.service-token.signing-key:}}")
            String signingKey,
            @Value("${portal.auth.oauth-state.ttl:PT5M}") Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
        this.signingKey = resolveSigningKey(signingKey);
        this.ttl = requirePositive(ttl);
    }

    /**
     * GitHub authorize URL에 실을 서명된 단기 state 값을 만든다.
     */
    public String createState() {
        return createSignedState().value();
    }

    /**
     * GitHub authorize URL에 실을 state와 DB에 저장할 nonce hash metadata를 함께 만든다.
     */
    public SignedState createSignedState() {
        Instant expiresAt = clock.instant().plus(ttl);
        String nonce = randomNonce();
        String payload = VERSION + "\n"
                + expiresAt.getEpochSecond() + "\n"
                + nonce;
        String encodedPayload = BASE64_URL_ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return new SignedState(
                encodedPayload + "." + sign(encodedPayload),
                hashNonce(nonce),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    /**
     * callback으로 돌아온 state의 서명과 만료 시각을 검증한다.
     */
    public boolean isValid(String state) {
        return verify(state).isPresent();
    }

    /**
     * callback state의 서명과 만료를 검증하고 nonce hash metadata를 반환한다.
     */
    public Optional<VerifiedState> verify(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        String trimmed = state.trim();
        int separator = trimmed.indexOf('.');
        if (separator <= 0 || separator != trimmed.lastIndexOf('.') || separator == trimmed.length() - 1) {
            return Optional.empty();
        }

        String encodedPayload = trimmed.substring(0, separator);
        String providedSignature = trimmed.substring(separator + 1);
        try {
            if (!constantTimeEquals(sign(encodedPayload), providedSignature)) {
                return Optional.empty();
            }
            String payload = new String(BASE64_URL_DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
            String[] parts = payload.split("\n", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0]) || parts[2].isBlank()) {
                return Optional.empty();
            }
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[1]));
            if (clock.instant().isAfter(expiresAt)) {
                return Optional.empty();
            }
            return Optional.of(new VerifiedState(
                    hashNonce(parts[2]),
                    OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String randomNonce() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return BASE64_URL_ENCODER.encodeToString(randomBytes);
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign OAuth state", exception);
        }
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                provided.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hashNonce(String nonce) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(nonce.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash OAuth state nonce", exception);
        }
    }

    private static byte[] resolveSigningKey(String configuredSigningKey) {
        String value = configuredSigningKey;
        if (value == null || value.isBlank()) {
            value = "development-oauth-state-" + UUID.randomUUID();
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Duration requirePositive(Duration value) {
        Duration required = Objects.requireNonNull(value, "ttl must not be null");
        if (required.isZero() || required.isNegative()) {
            throw new IllegalArgumentException("OAuth state ttl must be positive");
        }
        return required;
    }

    /**
     * authorize 시작 시 client로 보낼 signed state와 저장소에 남길 nonce hash metadata다.
     */
    public record SignedState(String value, String nonceHash, OffsetDateTime expiresAt) {
    }

    /**
     * callback으로 돌아온 signed state에서 검증된 nonce hash metadata다.
     */
    public record VerifiedState(String nonceHash, OffsetDateTime expiresAt) {
    }
}
