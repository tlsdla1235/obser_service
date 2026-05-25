package com.observation.portal.domain.bucket.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * raw instance bucket row를 application-level 30초 bucket evidence로 접는 bounded projection helper다.
 *
 * <p>같은 bucket boundary의 request/error count는 합산하고, duration histogram은 boundary set이 모두 일치할 때만
 * cumulative count를 boundary별로 합산한다. mismatch/invalid histogram은 빈 배열로 내려 service가 latency bad로
 * 세지 않게 한다.</p>
 */
public final class RecentBucketEvidenceRows {

    private static final String INVALID_DURATION_BUCKETS_JSON = "[]";

    private RecentBucketEvidenceRows() {
    }

    /**
     * 최신순 raw evidence row를 distinct application bucket boundary 기준으로 합쳐 최대 limit개만 반환한다.
     *
     * <p>이 helper는 bad bucket, degraded, rule confidence를 판단하지 않고 repository/service가 공유하는 projection
     * boundary 정규화만 수행한다.</p>
     */
    public static List<RecentBucketEvidenceRow> applicationLevelBuckets(
            List<RecentBucketEvidenceRow> rows,
            int limit,
            ObjectMapper objectMapper) {
        List<RecentBucketEvidenceRow> evidenceRows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
        ObjectMapper requiredObjectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        if (limit <= 0 || evidenceRows.isEmpty()) {
            return List.of();
        }

        Map<BucketBoundary, List<RecentBucketEvidenceRow>> rowsByBoundary = new LinkedHashMap<>();
        evidenceRows.stream()
                .sorted(Comparator.comparing(RecentBucketEvidenceRow::bucketEndUtc).reversed()
                        .thenComparing(RecentBucketEvidenceRow::bucketStartUtc, Comparator.reverseOrder())
                        .thenComparing(row -> row.applicationId().toString()))
                .forEach(row -> rowsByBoundary
                        .computeIfAbsent(BucketBoundary.from(row), ignored -> new ArrayList<>())
                        .add(row));

        return rowsByBoundary.entrySet().stream()
                .limit(limit)
                .map(entry -> mergeBoundaryRows(entry.getKey(), entry.getValue(), requiredObjectMapper))
                .toList();
    }

    private static RecentBucketEvidenceRow mergeBoundaryRows(
            BucketBoundary boundary,
            List<RecentBucketEvidenceRow> rows,
            ObjectMapper objectMapper) {
        long requestCount = rows.stream().mapToLong(RecentBucketEvidenceRow::requestCount).sum();
        long errorCount = rows.stream().mapToLong(RecentBucketEvidenceRow::errorCount).sum();
        return new RecentBucketEvidenceRow(
                boundary.applicationId(),
                boundary.bucketStartUtc(),
                boundary.bucketEndUtc(),
                requestCount,
                errorCount,
                mergeDurationBuckets(rows, objectMapper));
    }

    private static String mergeDurationBuckets(List<RecentBucketEvidenceRow> rows, ObjectMapper objectMapper) {
        List<Long> expectedBoundarySet = null;
        Map<Long, Long> mergedCounts = new LinkedHashMap<>();
        for (RecentBucketEvidenceRow row : rows) {
            Optional<List<DurationBucket>> parsedBuckets = parseDurationBuckets(row.durationBucketsJson(), objectMapper);
            if (parsedBuckets.isEmpty() || parsedBuckets.orElseThrow().isEmpty()) {
                return INVALID_DURATION_BUCKETS_JSON;
            }

            List<DurationBucket> buckets = sortedUniqueBuckets(parsedBuckets.orElseThrow());
            if (buckets.isEmpty()) {
                return INVALID_DURATION_BUCKETS_JSON;
            }
            List<Long> boundarySet = buckets.stream()
                    .map(DurationBucket::leMs)
                    .toList();
            if (expectedBoundarySet == null) {
                expectedBoundarySet = boundarySet;
                boundarySet.forEach(boundary -> mergedCounts.put(boundary, 0L));
            } else if (!expectedBoundarySet.equals(boundarySet)) {
                return INVALID_DURATION_BUCKETS_JSON;
            }

            for (DurationBucket bucket : buckets) {
                mergedCounts.compute(bucket.leMs(), (boundary, count) -> count + bucket.count());
            }
        }

        try {
            return objectMapper.writeValueAsString(mergedCounts.entrySet().stream()
                    .map(entry -> new DurationBucket(entry.getKey(), entry.getValue()))
                    .toList());
        } catch (JsonProcessingException exception) {
            return INVALID_DURATION_BUCKETS_JSON;
        }
    }

    private static Optional<List<DurationBucket>> parseDurationBuckets(String json, ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return Optional.empty();
            }
            List<DurationBucket> buckets = new ArrayList<>();
            for (JsonNode item : root) {
                JsonNode leMs = item.get("leMs");
                JsonNode count = item.get("count");
                if (leMs == null || count == null || !leMs.canConvertToLong() || !count.canConvertToLong()) {
                    return Optional.empty();
                }
                long boundary = leMs.asLong();
                long cumulativeCount = count.asLong();
                if (boundary < 0L || cumulativeCount < 0L) {
                    return Optional.empty();
                }
                buckets.add(new DurationBucket(boundary, cumulativeCount));
            }
            return Optional.of(buckets);
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private static List<DurationBucket> sortedUniqueBuckets(List<DurationBucket> buckets) {
        List<DurationBucket> sortedBuckets = buckets.stream()
                .sorted(Comparator.comparingLong(DurationBucket::leMs))
                .toList();
        Long previousBoundary = null;
        for (DurationBucket bucket : sortedBuckets) {
            if (Objects.equals(previousBoundary, bucket.leMs())) {
                return List.of();
            }
            previousBoundary = bucket.leMs();
        }
        return sortedBuckets;
    }

    private record BucketBoundary(
            UUID applicationId,
            OffsetDateTime bucketStartUtc,
            OffsetDateTime bucketEndUtc
    ) {

        private static BucketBoundary from(RecentBucketEvidenceRow row) {
            return new BucketBoundary(row.applicationId(), row.bucketStartUtc(), row.bucketEndUtc());
        }
    }

    private record DurationBucket(long leMs, long count) {
    }
}
