package com.observation.portal.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BCrypt 입력 한계 주변의 project key 검증 방어를 고정하는 단위 테스트다.
 */
class ProjectKeyHashVerifierTest {

    private final ProjectKeyHashVerifier verifier = new ProjectKeyHashVerifier();

    @Test
    void rejectsProjectKeyLongerThanBcryptInputLimitEvenWhenTruncatedPrefixMatchesHash() {
        String seventyTwoByteKey = "a".repeat(72);
        String hash = BCrypt.hashpw(seventyTwoByteKey, BCrypt.gensalt());

        assertThat(verifier.matches(seventyTwoByteKey, hash)).isTrue();
        assertThat(verifier.matches(seventyTwoByteKey + "b", hash)).isFalse();
    }

    @Test
    void usesUtf8ByteLengthForBcryptInputLimit() {
        String seventyTwoByteKey = "가".repeat(24);
        String hash = BCrypt.hashpw(seventyTwoByteKey, BCrypt.gensalt());

        assertThat(verifier.matches(seventyTwoByteKey, hash)).isTrue();
        assertThat(verifier.matches(seventyTwoByteKey + "a", hash)).isFalse();
    }
}
