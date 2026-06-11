package com.observation.portal.domain.history.service;

import com.observation.portal.domain.history.model.OperationalEventHistoryReadModel;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operational event history query parameter의 default/clamp/validation 결과를 담는 value object다.
 */
public record OperationalEventHistoryQuery(
        String requestedSince,
        OffsetDateTime since,
        OffsetDateTime until,
        int limit
) {

    private static final Pattern SINCE_PATTERN = Pattern.compile("^([1-9][0-9]*)([hd])$");
    private static final Duration MAX_SINCE = Duration.ofDays(14);

    /**
     * `since`와 `limit` raw query 값을 Story 5.9-a 정책에 맞는 effective query로 변환한다.
     */
    public static OperationalEventHistoryQuery from(String since, String limit, Clock clock) {
        return from(since, limit, clock, 14);
    }

    /**
     * `since`와 `limit` raw query 값을 snapshot retention horizon 안으로 clamp해 변환한다.
     */
    public static OperationalEventHistoryQuery from(String since, String limit, Clock clock, int retentionDays) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        Clock utcClock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
        String requestedSince = requestedSince(since);
        Duration effectiveDuration = effectiveSince(requestedSince, retentionDays);
        OffsetDateTime until = OffsetDateTime.ofInstant(utcClock.instant(), ZoneOffset.UTC);
        return new OperationalEventHistoryQuery(
                requestedSince,
                until.minus(effectiveDuration),
                until,
                effectiveLimit(limit));
    }

    private static String requestedSince(String since) {
        if (since == null) {
            return OperationalEventHistoryReadModel.DEFAULT_SINCE;
        }
        String normalized = since.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new InvalidOperationalEventHistoryQueryException("since must not be blank");
        }
        if (!SINCE_PATTERN.matcher(normalized).matches()) {
            throw new InvalidOperationalEventHistoryQueryException("since must be positive integer plus h or d");
        }
        return normalized;
    }

    private static Duration effectiveSince(String requestedSince, int retentionDays) {
        Matcher matcher = SINCE_PATTERN.matcher(requestedSince);
        if (!matcher.matches()) {
            throw new InvalidOperationalEventHistoryQueryException("since must be positive integer plus h or d");
        }
        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new InvalidOperationalEventHistoryQueryException("since amount is too large");
        }
        Duration requested;
        try {
            requested = "h".equals(matcher.group(2)) ? Duration.ofHours(amount) : Duration.ofDays(amount);
        } catch (ArithmeticException exception) {
            requested = MAX_SINCE.plusSeconds(1);
        }
        Duration retentionBound = Duration.ofDays(Math.min(14, retentionDays));
        Duration upperBound = retentionBound.compareTo(MAX_SINCE) <= 0 ? retentionBound : MAX_SINCE;
        return requested.compareTo(upperBound) <= 0 ? requested : upperBound;
    }

    private static int effectiveLimit(String limit) {
        if (limit == null) {
            return OperationalEventHistoryReadModel.DEFAULT_LIMIT;
        }
        String normalized = limit.trim();
        if (normalized.isEmpty()) {
            throw new InvalidOperationalEventHistoryQueryException("limit must not be blank");
        }
        int parsedLimit;
        try {
            parsedLimit = Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new InvalidOperationalEventHistoryQueryException("limit must be an integer");
        }
        if (parsedLimit <= 0) {
            throw new InvalidOperationalEventHistoryQueryException("limit must be positive");
        }
        return Math.min(parsedLimit, OperationalEventHistoryReadModel.MAX_LIMIT);
    }
}
