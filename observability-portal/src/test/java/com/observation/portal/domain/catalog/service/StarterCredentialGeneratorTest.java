package com.observation.portal.domain.catalog.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class StarterCredentialGeneratorTest {

    @Test
    void generatedCredentialUsesPrefixSecretShapeWithinBcryptAndPrefixLimits() {
        StarterCredentialGenerator generator = new StarterCredentialGenerator(new SecureRandom());

        var credential = generator.generate();

        assertThat(credential.displayValue()).startsWith(credential.keyPrefix() + ".");
        assertThat(credential.keyPrefix()).startsWith("obs_live_");
        assertThat(credential.keyPrefix()).hasSizeLessThanOrEqualTo(32);
        assertThat(credential.displayValue().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(72);
        assertThat(credential.displayValue().substring(credential.keyPrefix().length() + 1)).isNotBlank();
    }

    @Test
    void generatorDoesNotDeclareLoggerThatCouldCaptureRawCredential() {
        assertThat(StarterCredentialGenerator.class.getDeclaredFields())
                .noneMatch(field -> Logger.class.isAssignableFrom(field.getType()));
    }
}
