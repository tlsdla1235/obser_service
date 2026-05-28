package com.observation.portal.domain.account.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.account.model.ServiceTokenPair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 짧은 만료 JWT access token과 원문을 저장하지 않는 refresh token 값을 발급하고 검증한다.
 */
@Component
public class ServiceTokenIssuer {

    private static final String ISSUER = "observation-portal";
    private static final String PROVIDER_GITHUB = "github";
    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Clock clock;
    private final byte[] signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * service token 발급에 필요한 clock, signing key, TTL을 주입한다.
     */
    public ServiceTokenIssuer(
            Clock clock,
            @Value("${portal.auth.service-token.signing-key:}") String signingKey,
            @Value("${portal.auth.service-token.access-token-ttl:PT15M}") Duration accessTokenTtl,
            @Value("${portal.auth.service-token.refresh-token-ttl:P30D}") Duration refreshTokenTtl) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
        this.signingKey = resolveSigningKey(signingKey);
        this.accessTokenTtl = Objects.requireNonNull(accessTokenTtl, "accessTokenTtl must not be null");
        this.refreshTokenTtl = Objects.requireNonNull(refreshTokenTtl, "refreshTokenTtl must not be null");
    }

    /**
     * account id를 subject로 하는 access token과 새 refresh token 원문을 발급한다.
     */
    public ServiceTokenPair issue(UUID accountId, String refreshToken) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        String requiredRefreshToken = requireText(refreshToken, "refreshToken");
        var now = clock.instant();
        var accessExpiresAt = now.plus(accessTokenTtl);
        var refreshExpiresAt = now.plus(refreshTokenTtl);
        return new ServiceTokenPair(
                TOKEN_TYPE_BEARER,
                jwt(accountId, now.getEpochSecond(), accessExpiresAt.getEpochSecond()),
                OffsetDateTime.ofInstant(accessExpiresAt, ZoneOffset.UTC),
                requiredRefreshToken,
                OffsetDateTime.ofInstant(refreshExpiresAt, ZoneOffset.UTC));
    }

    /**
     * resource API의 Bearer access token을 service JWT로 검증하고 subject account id를 반환한다.
     *
     * <p>GitHub provider token이나 형식이 다른 token은 service token으로 인정하지 않는다.</p>
     */
    public Optional<UUID> verifyAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }
        String[] segments = accessToken.trim().split("\\.", -1);
        if (segments.length != 3 || segments[0].isBlank() || segments[1].isBlank() || segments[2].isBlank()) {
            return Optional.empty();
        }

        String signingInput = segments[0] + "." + segments[1];
        if (!signatureMatches(signingInput, segments[2])) {
            return Optional.empty();
        }

        try {
            JsonNode header = readJsonSegment(segments[0]);
            JsonNode payload = readJsonSegment(segments[1]);
            if (!isSupportedHeader(header) || !isServiceAccessTokenPayload(payload)) {
                return Optional.empty();
            }
            long issuedAt = payload.get("iat").longValue();
            long expiresAt = payload.get("exp").longValue();
            long now = clock.instant().getEpochSecond();
            if (issuedAt > now || expiresAt <= now) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(payload.get("sub").asText()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * 클라이언트에 한 번만 전달할 refresh token 원문을 만든다.
     */
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return BASE64_URL.encodeToString(randomBytes);
    }

    /**
     * 저장소 lookup에 사용할 SHA-256 refresh token hash를 만든다.
     */
    public String hashRefreshToken(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(requireText(refreshToken, "refreshToken").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is required", exception);
        }
    }

    /**
     * refresh token 만료 시각을 현재 clock 기준으로 계산한다.
     */
    public OffsetDateTime refreshTokenExpiresAt() {
        return OffsetDateTime.ofInstant(clock.instant().plus(refreshTokenTtl), ZoneOffset.UTC);
    }

    private String jwt(UUID accountId, long issuedAt, long expiresAt) {
        String header = base64Json("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Json(("{\"iss\":\"%s\",\"sub\":\"%s\","
                + "\"provider\":\"%s\",\"iat\":%d,\"exp\":%d}")
                .formatted(ISSUER, accountId, PROVIDER_GITHUB, issuedAt, expiresAt));
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    private String sign(String content) {
        return BASE64_URL.encodeToString(signatureBytes(content));
    }

    private boolean signatureMatches(String signingInput, String encodedSignature) {
        try {
            byte[] actual = BASE64_URL_DECODER.decode(encodedSignature);
            return MessageDigest.isEqual(signatureBytes(signingInput), actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] signatureBytes(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign service access token", exception);
        }
    }

    private static String base64Json(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode readJsonSegment(String segment) throws java.io.IOException {
        return OBJECT_MAPPER.readTree(BASE64_URL_DECODER.decode(segment));
    }

    private static boolean isSupportedHeader(JsonNode header) {
        return textEquals(header, "alg", "HS256") && textEquals(header, "typ", "JWT");
    }

    private static boolean isServiceAccessTokenPayload(JsonNode payload) {
        return textEquals(payload, "iss", ISSUER)
                && textEquals(payload, "provider", PROVIDER_GITHUB)
                && hasText(payload, "sub")
                && hasLong(payload, "iat")
                && hasLong(payload, "exp");
    }

    private static boolean textEquals(JsonNode node, String fieldName, String expected) {
        return node.hasNonNull(fieldName) && expected.equals(node.get(fieldName).asText());
    }

    private static boolean hasText(JsonNode node, String fieldName) {
        return node.hasNonNull(fieldName) && !node.get(fieldName).asText().isBlank();
    }

    private static boolean hasLong(JsonNode node, String fieldName) {
        return node.hasNonNull(fieldName) && node.get(fieldName).canConvertToLong();
    }

    private static byte[] resolveSigningKey(String configuredSigningKey) {
        String value = configuredSigningKey;
        if (value == null || value.isBlank()) {
            value = "development-ephemeral-" + UUID.randomUUID();
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
