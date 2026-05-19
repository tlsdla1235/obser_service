package com.observation.portal.security;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * project key 원문과 DB의 BCrypt hash를 비교하는 보안 보조 컴포넌트다.
 *
 * <p>검증 실패는 예외나 로그 대신 boolean으로만 반환해 원문 key가 외부로 새지 않게 한다.
 * BCrypt의 72 byte 입력 한계를 넘는 key는 truncation collision을 피하기 위해 비교하지 않는다.</p>
 */
@Component
public class ProjectKeyHashVerifier {

    private static final int MAX_BCRYPT_INPUT_BYTES = 72;

    /**
     * 원문 project key가 저장된 BCrypt hash와 일치하는지 확인한다.
     */
    public boolean matches(String rawProjectKey, String projectKeyHash) {
        if (rawProjectKey == null || rawProjectKey.isBlank()) {
            return false;
        }
        if (exceedsBcryptInputLimit(rawProjectKey)) {
            return false;
        }
        if (projectKeyHash == null || projectKeyHash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawProjectKey, projectKeyHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean exceedsBcryptInputLimit(String rawProjectKey) {
        return rawProjectKey.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_INPUT_BYTES;
    }
}
