package com.observation.portal.domain.ingest.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * benchmark output publish 전에 secret, queue URL, raw payload marker가 없는지 검사하는 allow-list guard다.
 */
public class IngestBenchmarkRedactionScanner {

    private static final Pattern AWS_ACCESS_KEY = Pattern.compile("(AKIA|ASIA)[0-9A-Z]{16}");

    /**
     * 단일 text artifact에서 금지 marker를 찾아 violation 목록으로 반환한다.
     */
    public ScanResult findViolations(String content) {
        String text = Objects.requireNonNull(content, "content must not be null");
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> violations = new ArrayList<>();
        if (lower.contains("https://sqs.") || lower.contains("queueurl=") || lower.contains("queue-url=")) {
            violations.add("queue_url");
        }
        if (lower.contains("projectkey=")
                || lower.contains("project_key=")
                || lower.contains("raw project key")
                || lower.contains("observation_smoke_project_key")
                || lower.contains("ecc_endpoint_smoke_project_key")) {
            violations.add("raw_project_key");
        }
        if (lower.contains("startercredential=")
                || lower.contains("starter_credential=")
                || lower.contains("starter credential")) {
            violations.add("starter_credential");
        }
        if (lower.contains("authorization: bearer") || lower.contains("authorization=bearer")) {
            violations.add("authorization_token");
        }
        if (lower.contains("access_token")
                || lower.contains("refresh_token")
                || lower.contains("session_token")
                || lower.contains("token=")
                || lower.contains("\"token\":")) {
            violations.add("token");
        }
        if (lower.contains("discord.com/api/webhooks")) {
            violations.add("discord_webhook");
        }
        if (AWS_ACCESS_KEY.matcher(text).find()
                || lower.contains("aws_access_key_id")
                || lower.contains("aws_secret_access_key")
                || lower.contains("aws_session_token")) {
            violations.add("aws_credential");
        }
        if (lower.contains("rawpayload")
                || lower.contains("raw_payload")
                || lower.contains("raw payload")
                || lower.contains("\"payload\":")
                || lower.contains("\"schemaversion\":")) {
            violations.add("raw_payload");
        }
        return new ScanResult(List.copyOf(violations));
    }

    /**
     * violation이 하나라도 있으면 benchmark artifact publish를 중단할 수 있게 예외를 던진다.
     */
    public void assertSafe(String content) {
        ScanResult result = findViolations(content);
        if (!result.violations().isEmpty()) {
            throw new IllegalArgumentException("benchmark output redaction violations: " + result.violations());
        }
    }

    /**
     * benchmark output directory의 regular file들을 순회하며 redaction violation을 검사한다.
     */
    public ScanResult scanDirectory(Path outputDirectory) {
        Path requiredDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(requiredDirectory)) {
            files.filter(Files::isRegularFile)
                    .forEach(path -> violations.addAll(findViolations(read(path)).violations()));
        } catch (IOException exception) {
            throw new IllegalStateException("benchmark output redaction scan failed", exception);
        }
        return new ScanResult(List.copyOf(violations));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("benchmark output read failed", exception);
        }
    }

    /**
     * redaction scan에서 발견한 금지 marker code 목록이다.
     */
    public record ScanResult(List<String> violations) {

        public ScanResult {
            violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
        }
    }
}
