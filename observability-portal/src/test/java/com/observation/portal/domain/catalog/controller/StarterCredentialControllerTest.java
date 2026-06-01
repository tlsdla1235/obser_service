package com.observation.portal.domain.catalog.controller;

import com.observation.portal.domain.account.controller.AccountProjectMembershipResourceApiInterceptor;
import com.observation.portal.domain.account.controller.BearerResourceApiInterceptor;
import com.observation.portal.domain.account.service.AccountProjectMembershipService;
import com.observation.portal.domain.account.service.ServiceTokenIssuer;
import com.observation.portal.domain.catalog.dto.StarterCredentialRotationResponse;
import com.observation.portal.domain.catalog.model.StarterCredentialDisplay;
import com.observation.portal.domain.catalog.model.StarterCredentialMetadata;
import com.observation.portal.domain.catalog.model.StarterCredentialRotationResult;
import com.observation.portal.domain.catalog.model.StarterCredentialStatus;
import com.observation.portal.domain.catalog.service.StarterCredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StarterCredentialControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000009261");
    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000009262");
    private static final Instant NOW = Instant.parse("2026-06-01T11:00:00Z");
    private static final OffsetDateTime ISSUED_AT = OffsetDateTime.parse("2026-06-01T10:00:00Z");
    private static final OffsetDateTime ROTATED_AT = OffsetDateTime.parse("2026-06-01T11:00:00Z");
    private static final OffsetDateTime REVOKED_AT = OffsetDateTime.parse("2026-06-01T12:00:00Z");
    private static final String RAW_ROTATED = "obs_live_rotate.<shown-once-rotated>";
    private static final String KEY_PREFIX = "obs_live_rotate";

    private final MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
    private final ServiceTokenIssuer tokenIssuer = new ServiceTokenIssuer(
            clock,
            "starter-credential-controller-test-signing-key",
            Duration.ofMinutes(15),
            Duration.ofDays(30));
    private final StarterCredentialService credentialService = mock(StarterCredentialService.class);
    private final AccountProjectMembershipService membershipService = mock(AccountProjectMembershipService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new StarterCredentialController(credentialService))
            .addMappedInterceptors(
                    new String[] {"/api/projects", "/api/projects/**"},
                    new BearerResourceApiInterceptor(tokenIssuer))
            .addMappedInterceptors(
                    new String[] {
                            "/api/projects/*/starter-credential",
                            "/api/projects/*/starter-credential/**"
                    },
                    new AccountProjectMembershipResourceApiInterceptor(membershipService))
            .build();

    @Test
    void metadataReadReturnsOnlyNonSecretCredentialFields() throws Exception {
        when(membershipService.hasActiveMembership(ACCOUNT_ID, PROJECT_ID)).thenReturn(true);
        when(credentialService.metadata(PROJECT_ID))
                .thenReturn(activeMetadata());

        mockMvc.perform(get(metadataPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.starterCredential.keyPrefix").value(KEY_PREFIX))
                .andExpect(jsonPath("$.starterCredential.status").value("active"))
                .andExpect(jsonPath("$.starterCredential.issuedAt").value("2026-06-01T10:00:00Z"))
                .andExpect(jsonPath("$.starterCredential.rotatedAt").doesNotExist())
                .andExpect(jsonPath("$.starterCredential.revokedAt").doesNotExist())
                .andExpect(content().string(not(containsString("displayValue"))))
                .andExpect(content().string(not(containsString("projectKeyHash"))))
                .andExpect(content().string(not(containsString(RAW_ROTATED))));

        verify(credentialService).metadata(PROJECT_ID);
    }

    @Test
    void rotationReturnsOneTimeRawValueWithNoStore() throws Exception {
        when(membershipService.hasActiveMembership(ACCOUNT_ID, PROJECT_ID)).thenReturn(true);
        when(credentialService.rotate(PROJECT_ID))
                .thenReturn(new StarterCredentialRotationResult(
                        PROJECT_ID,
                        new StarterCredentialDisplay(RAW_ROTATED, KEY_PREFIX, true, ROTATED_AT),
                        rotatedMetadata()));

        mockMvc.perform(post(rotationPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.starterCredential.displayValue").value(RAW_ROTATED))
                .andExpect(jsonPath("$.starterCredential.keyPrefix").value(KEY_PREFIX))
                .andExpect(jsonPath("$.starterCredential.status").value("active"))
                .andExpect(jsonPath("$.starterCredential.visibleOnce").value(true))
                .andExpect(jsonPath("$.starterCredential.rotatedAt").value("2026-06-01T11:00:00Z"))
                .andExpect(content().string(not(containsString("projectKeyHash"))));

        verify(credentialService).rotate(PROJECT_ID);
    }

    @Test
    void revocationReturnsOnlyMetadataWithoutRawValue() throws Exception {
        when(membershipService.hasActiveMembership(ACCOUNT_ID, PROJECT_ID)).thenReturn(true);
        when(credentialService.revoke(PROJECT_ID)).thenReturn(revokedMetadata());

        mockMvc.perform(post(revocationPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.starterCredential.keyPrefix").value(KEY_PREFIX))
                .andExpect(jsonPath("$.starterCredential.status").value("revoked"))
                .andExpect(jsonPath("$.starterCredential.revokedAt").value("2026-06-01T12:00:00Z"))
                .andExpect(content().string(not(containsString("displayValue"))))
                .andExpect(content().string(not(containsString(RAW_ROTATED))))
                .andExpect(content().string(not(containsString("projectKeyHash"))));

        verify(credentialService).revoke(PROJECT_ID);
    }

    @Test
    void membershipMismatchFailsClosedBeforeCredentialServiceLookup() throws Exception {
        when(membershipService.hasActiveMembership(ACCOUNT_ID, PROJECT_ID)).thenReturn(false);

        mockMvc.perform(get(metadataPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        mockMvc.perform(post(rotationPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        mockMvc.perform(post(revocationPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        verify(membershipService, times(3)).hasActiveMembership(ACCOUNT_ID, PROJECT_ID);
        verifyNoInteractions(credentialService);
    }

    @Test
    void rotationResponseToStringRedactsOneTimeRawValue() {
        StarterCredentialRotationResponse response = StarterCredentialRotationResponse.from(
                new StarterCredentialRotationResult(
                        PROJECT_ID,
                        new StarterCredentialDisplay(RAW_ROTATED, KEY_PREFIX, true, ROTATED_AT),
                        rotatedMetadata()));

        assertThat(response.toString()).doesNotContain(RAW_ROTATED);
        assertThat(response.starterCredential().toString()).doesNotContain(RAW_ROTATED);
    }

    @Test
    void noTokenInvalidTokenAndExpiredTokenReturnBearerUnauthorized() throws Exception {
        mockMvc.perform(post(rotationPath()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        mockMvc.perform(post(rotationPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-service-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        String expiringToken = accessToken();
        clock.set(Instant.parse("2026-06-01T11:16:00Z"));
        mockMvc.perform(post(rotationPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiringToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        verifyNoInteractions(membershipService, credentialService);
    }

    private String accessToken() {
        return tokenIssuer.issue(ACCOUNT_ID, "refresh-token-for-test").accessToken();
    }

    private static String metadataPath() {
        return "/api/projects/%s/starter-credential".formatted(PROJECT_ID);
    }

    private static String rotationPath() {
        return "/api/projects/%s/starter-credential/rotations".formatted(PROJECT_ID);
    }

    private static String revocationPath() {
        return "/api/projects/%s/starter-credential/revocations".formatted(PROJECT_ID);
    }

    private static StarterCredentialMetadata activeMetadata() {
        return new StarterCredentialMetadata(
                PROJECT_ID,
                KEY_PREFIX,
                StarterCredentialStatus.ACTIVE,
                ISSUED_AT,
                null,
                null);
    }

    private static StarterCredentialMetadata rotatedMetadata() {
        return new StarterCredentialMetadata(
                PROJECT_ID,
                KEY_PREFIX,
                StarterCredentialStatus.ACTIVE,
                ROTATED_AT,
                ROTATED_AT,
                null);
    }

    private static StarterCredentialMetadata revokedMetadata() {
        return new StarterCredentialMetadata(
                PROJECT_ID,
                KEY_PREFIX,
                StarterCredentialStatus.REVOKED,
                ISSUED_AT,
                null,
                REVOKED_AT);
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this(new AtomicReference<>(instant), zone);
        }

        private MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void set(Instant instant) {
            this.instant.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
