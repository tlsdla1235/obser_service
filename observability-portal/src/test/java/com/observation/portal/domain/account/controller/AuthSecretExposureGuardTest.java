package com.observation.portal.domain.account.controller;

import com.observation.portal.domain.account.model.AccountAuthResult;
import com.observation.portal.domain.account.model.GithubOAuthCallbackCommand;
import com.observation.portal.domain.account.model.ServiceTokenPair;
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
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSecretExposureGuardTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000006101");
    private static final OffsetDateTime ACCESS_EXPIRES_AT = OffsetDateTime.parse("2026-05-28T10:18:00Z");
    private static final OffsetDateTime REFRESH_EXPIRES_AT = OffsetDateTime.parse("2026-06-27T10:03:00Z");

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
    void browserGithubCallbackPageDoesNotRenderServiceTokenPairOrProviderInputs() throws Exception {
        when(authService.completeGithubCallback(new GithubOAuthCallbackCommand(
                "oauth-code-should-not-render",
                "browser-state",
                null)))
                .thenReturn(new AccountAuthResult(
                        ACCOUNT_ID,
                        "github",
                        new ServiceTokenPair(
                                "Bearer",
                                "access.jwt.value",
                                ACCESS_EXPIRES_AT,
                                "refresh-token-value",
                                REFRESH_EXPIRES_AT)));

        mockMvc.perform(get("/api/auth/github/callback")
                        .param("code", "oauth-code-should-not-render")
                        .param("state", "browser-state")
                        .param("providerAccessToken", "gho_raw_provider_token")
                        .param("client_secret", "raw-client-secret"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(content().string(containsString("GitHub 로그인 완료")))
                .andExpect(content().string(not(containsString("access.jwt.value"))))
                .andExpect(content().string(not(containsString("refresh-token-value"))))
                .andExpect(content().string(not(containsString("oauth-code-should-not-render"))))
                .andExpect(content().string(not(containsString("browser-state"))))
                .andExpect(content().string(not(containsString("gho_raw_provider_token"))))
                .andExpect(content().string(not(containsString("raw-client-secret"))))
                .andExpect(content().string(not(containsString("\"refreshToken\""))));
    }

    @Test
    void callbackRelayFailureDoesNotEchoRelayInputOrTokenLikePayload() throws Exception {
        mockMvc.perform(post("/api/auth/github/callback/tokens")
                        .contentType(APPLICATION_JSON)
                        .content("{\"relayId\":\"oauth-code-should-not-leak.gho_raw_provider_token.raw-client-secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(content().string(containsString("GitHub OAuth를 완료할 수 없습니다.")))
                .andExpect(content().string(not(containsString("oauth-code-should-not-leak"))))
                .andExpect(content().string(not(containsString("gho_raw_provider_token"))))
                .andExpect(content().string(not(containsString("raw-client-secret"))));
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
    void storySevenThreeSmokeScriptsAvoidUnsafeShellAndProjectKeyBearerBoundary() throws IOException {
        List<Path> smokeScripts = List.of(
                Path.of("..", "scripts", "smoke", "run-smoke-traffic.sh"),
                Path.of("..", "scripts", "smoke", "verify-smoke-portal-flow.sh"));

        for (Path script : smokeScripts) {
            assertThat(Files.exists(script))
                    .as("Story 7.3 smoke script must exist: %s", script)
                    .isTrue();
            String content = Files.readString(script);
            assertThat(content)
                    .as("smoke scripts must not enable shell trace, verbose curl, source auth env, or use project key as Bearer")
                    .doesNotContain(
                            "curl -v",
                            "set -x",
                            "source .private/smoke-auth.env",
                            "Authorization: Bearer ${OBSERVATION_SMOKE_PROJECT_KEY}");
            assertThat(content).contains("command -v", "jq");
        }

        String portalFlowScript = Files.readString(repoRoot().resolve("scripts/smoke/verify-smoke-portal-flow.sh"));
        assertThat(portalFlowScript)
                .contains(
                        "JWT-like three-segment shape",
                        "--connect-timeout",
                        "--max-time",
                        ".starterConnection.lastHeartbeatStatus == \"received\"",
                        "no_action_needed",
                        "observing_recovery");

        String trafficScript = Files.readString(repoRoot().resolve("scripts/smoke/run-smoke-traffic.sh"));
        assertThat(trafficScript).contains("--connect-timeout", "--max-time", ".private/smoke-project.env");
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

        ScriptResult rawProjectKeyRejected = runScript(
                script,
                tempDir,
                "smoke-key.fixture\n",
                List.of(),
                Map.of());
        assertThat(rawProjectKeyRejected.exitCode()).isNotZero();
        assertThat(rawProjectKeyRejected.combinedOutput()).doesNotContain("smoke-key.fixture");

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
                "OBSERVATION_SMOKE_ACCESS_TOKEN=smoke-key.fixture\n",
                "client_secret=blocked\n")) {
            Files.writeString(authFile, authContent, StandardCharsets.UTF_8);
            ScriptResult rejected = runScript(script, workDir, "", List.of(), Map.of());
            assertThat(rejected.exitCode()).isNotZero();
            assertThat(rejected.combinedOutput()).doesNotContain(serviceAccessToken, "blocked", "smoke-key.fixture");
        }
    }

    @Test
    void verifySmokePortalFlowRequiresJwtTokenProjectKeyAndReceivedHeartbeatShape() throws Exception {
        Path script = repoRoot().resolve("scripts/smoke/verify-smoke-portal-flow.sh");
        Path workDir = Files.createDirectory(tempDir.resolve("portal-flow-work"));
        Path authDir = Files.createDirectory(workDir.resolve(".private"));
        String serviceAccessToken = "header.payload.signature";
        String rawProjectKey = "smoke-key.fixture";
        Files.writeString(
                authDir.resolve("smoke-auth.env"),
                "OBSERVATION_SMOKE_ACCESS_TOKEN=" + serviceAccessToken + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                authDir.resolve("smoke-project.env"),
                "OBSERVATION_SMOKE_PROJECT_KEY=" + rawProjectKey + "\n",
                StandardCharsets.UTF_8);

        Path fakeBin = Files.createDirectory(tempDir.resolve("portal-flow-fake-bin"));
        writeFakePortalFlowCurl(fakeBin.resolve("curl"));
        String fakePath = fakeBin + File.pathSeparator + System.getenv("PATH");
        Map<String, String> baseEnvironment = Map.of(
                "PATH", fakePath,
                "OBSERVATION_SMOKE_WAIT_SECONDS", "0");

        ScriptResult success = runScript(script, workDir, "", List.of(), baseEnvironment);
        assertThat(success.exitCode()).isZero();
        assertThat(success.combinedOutput()).doesNotContain(serviceAccessToken, rawProjectKey);

        for (String badMode : List.of("missingHeartbeatStatus", "invalidZeroReason", "missingEvidenceHeartbeat")) {
            ScriptResult rejected = runScript(
                    script,
                    workDir,
                    "",
                    List.of(),
                    withEnvironment(baseEnvironment, "SMOKE_FAKE_PORTAL_FLOW_MODE", badMode));
            assertThat(rejected.exitCode()).isNotZero();
            assertThat(rejected.combinedOutput()).doesNotContain(serviceAccessToken, rawProjectKey);
        }

        Files.writeString(
                authDir.resolve("smoke-auth.env"),
                "OBSERVATION_SMOKE_ACCESS_TOKEN=" + rawProjectKey + "\n",
                StandardCharsets.UTF_8);
        ScriptResult rawKeyAsBearerRejected = runScript(script, workDir, "", List.of(), baseEnvironment);
        assertThat(rawKeyAsBearerRejected.exitCode()).isNotZero();
        assertThat(rawKeyAsBearerRejected.combinedOutput()).doesNotContain(rawProjectKey);

        Files.writeString(
                authDir.resolve("smoke-auth.env"),
                "OBSERVATION_SMOKE_ACCESS_TOKEN=" + serviceAccessToken + "\n",
                StandardCharsets.UTF_8);
        Files.delete(authDir.resolve("smoke-project.env"));
        ScriptResult missingProjectKeyRejected = runScript(script, workDir, "", List.of(), baseEnvironment);
        assertThat(missingProjectKeyRejected.exitCode()).isNotZero();
        assertThat(missingProjectKeyRejected.combinedOutput())
                .contains("Missing starter project key material")
                .doesNotContain(serviceAccessToken, rawProjectKey);
    }

    @Test
    void dashboardReadModelSnapshotHistoryAndInstanceSourcesDoNotExposeCredentialOrTokenFields() throws IOException {
        List<Path> sourceRoots = List.of(
                Path.of("src/main/java/com/observation/portal/domain/dashboard"),
                Path.of("src/main/java/com/observation/portal/domain/history"),
                Path.of("src/main/java/com/observation/portal/domain/snapshot"),
                Path.of("src/main/java/com/observation/portal/domain/instance"));
        List<String> forbidden = List.of(
                "starterCredential",
                "displayValue",
                "projectKeyHash",
                "project_key_hash",
                "accessToken",
                "refreshToken",
                "providerAccessToken");

        for (Path root : sourceRoots) {
            try (Stream<Path> files = Files.walk(root)) {
                List<String> leaks = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .sorted(Comparator.comparing(Path::toString))
                        .flatMap(path -> forbidden.stream()
                                .filter(snippet -> containsSnippet(path, snippet))
                                .map(snippet -> path + " -> " + snippet))
                        .toList();

                assertThat(leaks)
                        .as("dashboard/read model/snapshot/history/instance response sources must not expose secret fields")
                        .isEmpty();
            }
        }
    }

    @Test
    void frontendDashboardAllowsOneTimeDisplayButNoBrowserPersistenceOrRawCredentialAttributes() throws IOException {
        String page = Files.readString(Path.of(
                "..",
                "frontend",
                "src",
                "app",
                "components",
                "dashboard.tsx"));

        assertThat(page)
                .contains("credential.displayValue", "navigator.clipboard.writeText(credential.displayValue)")
                .doesNotContain(
                        "localStorage",
                        "sessionStorage",
                        "document.cookie",
                        "window.location.hash",
                        "window.location.search",
                        "URLSearchParams",
                        "type=\"hidden\"",
                        "data-starter-credential",
                        "data-credential-value",
                        "aria-label=\"${escapeAttribute(displayValue)}\"",
                        "title=\"${escapeAttribute(displayValue)}\"",
                        "console.log(displayValue)");
    }

    @Test
    void catalogLifecycleSourcesAndFixturesDoNotKeepRealisticRawCredentialSnippets() throws IOException {
        List<Path> sourceRoots = List.of(
                Path.of("src/main/java/com/observation/portal/domain/catalog"),
                Path.of("src/test/java/com/observation/portal/domain/catalog"),
                Path.of("src/test/java/com/observation/portal/domain/ingest"));
        List<String> forbidden = List.of(
                "repository-" + "secret",
                "obs_live_existing" + "." + "secret",
                "pk_live" + "." + "secret",
                "secret-part-kept-out-of-" + "results",
                "\"." + "secret\"");

        List<String> leaks = new ArrayList<>();
        for (Path root : sourceRoots) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(path -> forbidden.stream()
                                .filter(snippet -> containsSnippet(path, snippet))
                                .map(snippet -> path + " -> " + snippet)
                                .forEach(leaks::add));
            }
        }

        assertThat(leaks)
                .as("catalog lifecycle source/test fixtures should use placeholders, not realistic raw credentials")
                .isEmpty();
    }

    private static Path repoRoot() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    private static boolean containsSnippet(Path path, String snippet) {
        try {
            return Files.readString(path).contains(snippet);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read " + path, exception);
        }
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
                    -w|-H|--connect-timeout|--max-time)
                      shift 2
                      ;;
                    -sS)
                      shift
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

    private static void writeFakePortalFlowCurl(Path curlPath) throws IOException {
        Files.writeString(curlPath, """
                #!/bin/bash
                set -euo pipefail
                output_file=""
                auth_header=""
                url=""
                while [[ "$#" -gt 0 ]]; do
                  case "$1" in
                    -o)
                      output_file="$2"
                      shift 2
                      ;;
                    -H)
                      auth_header="$2"
                      shift 2
                      ;;
                    -w|--connect-timeout|--max-time)
                      shift 2
                      ;;
                    -sS)
                      shift
                      ;;
                    *)
                      url="$1"
                      shift
                      ;;
                  esac
                done
                if [[ -z "${output_file}" ]]; then
                  exit 2
                fi
                if [[ "${auth_header}" != "Authorization: Bearer header.payload.signature" ]]; then
                  exit 3
                fi
                if [[ "${auth_header}" == *"smoke-key.fixture"* ]]; then
                  exit 4
                fi
                mode="${SMOKE_FAKE_PORTAL_FLOW_MODE:-success}"
                project_id="00000000-0000-0000-0000-000000007201"
                application_id="00000000-0000-0000-0000-000000007203"
                case "${url}" in
                  */api/projects)
                    printf '{"projects":[{"projectId":"%s","name":"local-smoke","links":{"applications":"/api/projects/%s/applications"}}]}' "${project_id}" "${project_id}" > "${output_file}"
                    ;;
                  */api/projects/*/applications)
                    printf '{"applications":[{"applicationId":"%s","name":"observation-smoke-service","environment":"local-smoke","links":{"dashboard":"/api/projects/%s/applications/%s/dashboard"}}]}' "${application_id}" "${project_id}" "${application_id}" > "${output_file}"
                    ;;
                  */dashboard)
                    if [[ "${mode}" == "missingHeartbeatStatus" ]]; then
                      printf '{"application":{"lastAcceptedBucketAt":"2026-06-01T01:00:30Z"},"starterConnection":{"statusSource":"starter_heartbeat","lastHeartbeatAt":"2026-06-01T01:00:20Z","stateImpact":"none"},"state":{"code":"active"},"zeroInsight":{"reasonCode":"no_action_needed"},"instances":[{"links":{"evidence":"/api/projects/%s/applications/%s/instances/smoke-instance/evidence"}}]}' "${project_id}" "${application_id}" > "${output_file}"
                    elif [[ "${mode}" == "invalidZeroReason" ]]; then
                      printf '{"application":{"lastAcceptedBucketAt":"2026-06-01T01:00:30Z"},"starterConnection":{"statusSource":"starter_heartbeat","lastHeartbeatStatus":"received","lastHeartbeatAt":"2026-06-01T01:00:20Z","stateImpact":"none"},"state":{"code":"active"},"zeroInsight":{"reasonCode":"anything_goes"},"instances":[{"links":{"evidence":"/api/projects/%s/applications/%s/instances/smoke-instance/evidence"}}]}' "${project_id}" "${application_id}" > "${output_file}"
                    else
                      printf '{"application":{"lastAcceptedBucketAt":"2026-06-01T01:00:30Z"},"starterConnection":{"statusSource":"starter_heartbeat","lastHeartbeatStatus":"received","lastHeartbeatAt":"2026-06-01T01:00:20Z","stateImpact":"none"},"state":{"code":"active"},"zeroInsight":{"reasonCode":"no_action_needed"},"instances":[{"links":{"evidence":"/api/projects/%s/applications/%s/instances/smoke-instance/evidence"}}]}' "${project_id}" "${application_id}" > "${output_file}"
                    fi
                    ;;
                  */evidence)
                    if [[ "${mode}" == "missingEvidenceHeartbeat" ]]; then
                      printf '{"metricData":{"statusSource":"accepted_bucket"},"starterConnection":{"statusSource":"starter_heartbeat","lastHeartbeatAt":"2026-06-01T01:00:20Z","stateImpact":"none"},"starterPercentiles":{"status":"missing"}}' > "${output_file}"
                    else
                      printf '{"metricData":{"statusSource":"accepted_bucket"},"starterConnection":{"statusSource":"starter_heartbeat","lastHeartbeatStatus":"received","lastHeartbeatAt":"2026-06-01T01:00:20Z","stateImpact":"none"},"starterPercentiles":{"status":"missing"}}' > "${output_file}"
                    fi
                    ;;
                  *)
                    exit 5
                    ;;
                esac
                printf '200'
                """, StandardCharsets.UTF_8);
        assertThat(curlPath.toFile().setExecutable(true)).isTrue();
    }

    private static Map<String, String> withEnvironment(
            Map<String, String> baseEnvironment,
            String key,
            String value) {
        Map<String, String> mergedEnvironment = new java.util.HashMap<>(baseEnvironment);
        mergedEnvironment.put(key, value);
        return mergedEnvironment;
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
