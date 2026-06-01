package com.observation.portal.domain.account.controller;

import com.observation.portal.domain.account.model.GithubOAuthCallbackCommand;
import com.observation.portal.domain.account.service.AccountAuthException;
import com.observation.portal.domain.account.service.AccountAuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSecretExposureGuardTest {

    private final AccountAuthService authService = mock(AccountAuthService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AccountAuthController(authService))
            .build();

    @TempDir
    Path tempDir;

    @Test
    void oauthFailureResponseUsesGeneralizedCopyWithoutTokenRawPayloadOrSecret() throws Exception {
        when(authService.completeGithubCallback(new GithubOAuthCallbackCommand(
                "oauth-code-should-not-leak",
                "browser-state",
                "access_denied")))
                .thenThrow(new AccountAuthException(
                        "github_oauth_failed",
                        "GitHub OAuth를 완료할 수 없습니다."));

        mockMvc.perform(get("/api/auth/github/callback")
                        .param("code", "oauth-code-should-not-leak")
                        .param("state", "browser-state")
                        .param("error", "access_denied")
                        .param("providerAccessToken", "gho_raw_provider_token")
                        .param("client_secret", "raw-client-secret"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(content().string(containsString("GitHub OAuth를 완료할 수 없습니다.")))
                .andExpect(content().string(not(containsString("oauth-code-should-not-leak"))))
                .andExpect(content().string(not(containsString("gho_raw_provider_token"))))
                .andExpect(content().string(not(containsString("raw-client-secret"))))
                .andExpect(content().string(not(containsString("access_denied"))));
    }

    @Test
    void smokeRunbookAndStorySevenOneFixturesDoNotPersistTokenCredentialOrRawKeyExamples() throws IOException {
        List<Path> smokeArtifacts = List.of(
                Path.of("..", "implementation-artifacts", "real-github-oauth-smoke-runbook.md"),
                Path.of("src", "test", "java", "com", "observation", "portal", "domain", "admin", "service",
                        "SmokeProjectSeedServiceTest.java"));

        for (Path artifact : smokeArtifacts) {
            assertThat(Files.exists(artifact))
                    .as("Story 7.1 smoke artifact must exist: %s", artifact)
                    .isTrue();
            String content = Files.readString(artifact);
            assertThat(content)
                    .as("smoke artifact must not contain forbidden token/key persistence snippets: %s", artifact)
                    .doesNotContain(
                            "github_pat_",
                            "gho_",
                            "client_secret=",
                            "raw-client-secret",
                            "dev-secret-only",
                            "smoke_local.dev");
        }
    }

    @Test
    void writeSmokeAuthEnvOnlyAcceptsSingleServiceAccessTokenAndFixedPrivatePath() throws Exception {
        Path script = repoRoot().resolve("scripts/smoke/write-smoke-auth-env.sh");
        String serviceAccessToken = "header.payload.signature";

        ScriptResult jsonRejected = runScript(
                script,
                tempDir,
                "{\"accessToken\":\"" + serviceAccessToken + "\",\"refreshToken\":\"blocked\"}\n",
                List.of(),
                Map.of());
        assertThat(jsonRejected.exitCode()).isNotZero();
        assertThat(jsonRejected.combinedOutput()).doesNotContain(serviceAccessToken, "blocked");
        assertThat(Files.exists(tempDir.resolve(".private/smoke-auth.env"))).isFalse();

        ScriptResult pathRejected = runScript(
                script,
                tempDir,
                serviceAccessToken + "\n",
                List.of("tracked-smoke-auth.env"),
                Map.of());
        assertThat(pathRejected.exitCode()).isNotZero();
        assertThat(Files.exists(tempDir.resolve("tracked-smoke-auth.env"))).isFalse();

        ScriptResult metacharRejected = runScript(
                script,
                tempDir,
                serviceAccessToken + ";printf-leak\n",
                List.of(),
                Map.of());
        assertThat(metacharRejected.exitCode()).isNotZero();
        assertThat(metacharRejected.combinedOutput()).doesNotContain(serviceAccessToken);

        ScriptResult accepted = runScript(
                script,
                tempDir,
                serviceAccessToken + "\n",
                List.of(),
                Map.of());
        assertThat(accepted.exitCode()).isZero();
        assertThat(accepted.combinedOutput()).doesNotContain(serviceAccessToken);
        assertThat(Files.readString(tempDir.resolve(".private/smoke-auth.env")))
                .isEqualTo("OBSERVATION_SMOKE_ACCESS_TOKEN=" + serviceAccessToken + "\n");
    }

    @Test
    void verifySmokeProjectsParsesAuthFileAsDataAndValidatesProjectResponseShape() throws Exception {
        Path script = repoRoot().resolve("scripts/smoke/verify-smoke-projects.sh");
        Path workDir = Files.createDirectory(tempDir.resolve("verify-work"));
        Path authDir = Files.createDirectory(workDir.resolve(".private"));
        Path authFile = authDir.resolve("smoke-auth.env");
        String serviceAccessToken = "header.payload.signature";
        Files.writeString(
                authFile,
                "OBSERVATION_SMOKE_ACCESS_TOKEN=" + serviceAccessToken + "\n",
                StandardCharsets.UTF_8);

        Path fakeBin = Files.createDirectory(tempDir.resolve("fake-bin"));
        writeFakeCurl(fakeBin.resolve("curl"));
        String fakePath = fakeBin + File.pathSeparator + System.getenv("PATH");

        ScriptResult success = runScript(
                script,
                workDir,
                "",
                List.of(),
                Map.of(
                        "PATH", fakePath,
                        "SMOKE_FAKE_RESPONSE", """
                                {"projects":[{"projectId":"00000000-0000-0000-0000-000000007201","name":"local-smoke","links":{"applications":"/api/projects/00000000-0000-0000-0000-000000007201/applications"}}]}
                                """));
        assertThat(success.exitCode()).isZero();
        assertThat(success.combinedOutput()).doesNotContain(serviceAccessToken);

        for (String badResponse : List.of(
                "{\"projects\":[]}",
                "{\"projects\":[{\"projectId\":\"00000000-0000-0000-0000-000000007201\",\"name\":\"other\",\"links\":{\"applications\":\"/api/projects/00000000-0000-0000-0000-000000007201/applications\"}}]}",
                "{\"projects\":[{\"projectId\":\"00000000-0000-0000-0000-000000007201\",\"name\":\"local-smoke\",\"links\":{\"applications\":\"/api/projects/wrong/applications\"}}]}")) {
            ScriptResult rejected = runScript(
                    script,
                    workDir,
                    "",
                    List.of(),
                    Map.of("PATH", fakePath, "SMOKE_FAKE_RESPONSE", badResponse));
            assertThat(rejected.exitCode()).isNotZero();
            assertThat(rejected.combinedOutput()).doesNotContain(serviceAccessToken);
        }
    }

    @Test
    void verifySmokeProjectsRejectsExtraKeysAndForbiddenAuthContentBeforeCurl() throws Exception {
        Path script = repoRoot().resolve("scripts/smoke/verify-smoke-projects.sh");
        Path workDir = Files.createDirectory(tempDir.resolve("verify-auth-work"));
        Path authDir = Files.createDirectory(workDir.resolve(".private"));
        Path authFile = authDir.resolve("smoke-auth.env");
        String serviceAccessToken = "header.payload.signature";

        for (String authContent : List.of(
                "OBSERVATION_SMOKE_ACCESS_TOKEN=" + serviceAccessToken + "\nEXTRA_KEY=blocked\n",
                "OBSERVATION_SMOKE_REFRESH_TOKEN=blocked\n",
                "OBSERVATION_SMOKE_ACCESS_TOKEN=" + serviceAccessToken + ";blocked\n",
                "client_secret=blocked\n")) {
            Files.writeString(authFile, authContent, StandardCharsets.UTF_8);
            ScriptResult rejected = runScript(script, workDir, "", List.of(), Map.of());
            assertThat(rejected.exitCode()).isNotZero();
            assertThat(rejected.combinedOutput()).doesNotContain(serviceAccessToken, "blocked");
        }
    }

    private static Path repoRoot() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    private static void writeFakeCurl(Path curlPath) throws IOException {
        Files.writeString(curlPath, """
                #!/bin/bash
                set -euo pipefail
                output_file=""
                while [[ "$#" -gt 0 ]]; do
                  case "$1" in
                    -o)
                      output_file="$2"
                      shift 2
                      ;;
                    -w|-H)
                      shift 2
                      ;;
                    *)
                      shift
                      ;;
                  esac
                done
                if [[ -z "${output_file}" ]]; then
                  exit 2
                fi
                printf '%s' "${SMOKE_FAKE_RESPONSE}" > "${output_file}"
                printf '200'
                """, StandardCharsets.UTF_8);
        assertThat(curlPath.toFile().setExecutable(true)).isTrue();
    }

    private static ScriptResult runScript(
            Path script,
            Path workingDirectory,
            String input,
            List<String> args,
            Map<String, String> environment) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(script.toString());
        command.addAll(args);
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile());
        processBuilder.environment().putAll(environment);

        Process process = processBuilder.start();
        process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new ScriptResult(exitCode, stdout, stderr);
    }

    private record ScriptResult(int exitCode, String stdout, String stderr) {

        private String combinedOutput() {
            return stdout + stderr;
        }
    }
}
