package com.observation.portal.domain.catalog.service;

import com.observation.portal.domain.catalog.model.GeneratedStarterCredential;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * starter ingest 인증에 사용할 `<key_prefix>.<secret>` 형식의 credential 원문을 생성한다.
 *
 * <p>prefix는 DB lookup metadata로 저장할 수 있지만, secret suffix를 포함한 display value는 성공 응답에서만
 * 1회 전달하고 persistence에는 저장하지 않는다.</p>
 */
@Component
public class StarterCredentialGenerator {

    private static final String PREFIX_MARKER = "obs_live_";
    private static final int PREFIX_RANDOM_BYTES = 12;
    private static final int SECRET_RANDOM_BYTES = 24;
    private static final int MAX_KEY_PREFIX_LENGTH = 32;
    private static final int MAX_BCRYPT_INPUT_BYTES = 72;
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom;

    /**
     * production runtime에서 cryptographic random source를 사용해 generator를 구성한다.
     */
    public StarterCredentialGenerator() {
        this(new SecureRandom());
    }

    /**
     * 테스트에서 deterministic 또는 별도 random source를 주입할 수 있게 한다.
     */
    public StarterCredentialGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    /**
     * BCrypt 입력 한계와 prefix 길이 제약 안에 들어오는 새 starter credential을 생성한다.
     */
    public GeneratedStarterCredential generate() {
        String keyPrefix = PREFIX_MARKER + randomUrlToken(PREFIX_RANDOM_BYTES);
        String secret = randomUrlToken(SECRET_RANDOM_BYTES);
        String displayValue = keyPrefix + "." + secret;
        if (keyPrefix.length() > MAX_KEY_PREFIX_LENGTH
                || displayValue.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_INPUT_BYTES) {
            throw new IllegalStateException("Generated starter credential exceeds configured storage constraints");
        }
        return new GeneratedStarterCredential(displayValue, keyPrefix);
    }

    private String randomUrlToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return BASE64_URL.encodeToString(bytes);
    }
}
