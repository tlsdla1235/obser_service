package com.observation.portal.common.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * dashboard query 시점의 recent 30 minutes 판단 window 묶음이다.
 *
 * <p>후속 DashboardReadModelService와 InstanceEvidenceReadModelService가 같은 30분 accepted bucket window 계약을
 * 사용하도록 query 시각, recent window, compatibility baseline, bucket duration을 하나의 값으로 전달한다.</p>
 */
public record DashboardTimeWindow(
        Instant queryAtUtc,
        UtcTimeInterval recent30Minutes,
        UtcTimeInterval baseline,
        Duration bucketDuration
) {

    /**
     * recent 30분 window는 query 시각에서 끝난다. baseline은 legacy compatibility 용도이므로 null을 허용한다.
     */
    public DashboardTimeWindow {
        Objects.requireNonNull(queryAtUtc, "queryAtUtc must not be null");
        Objects.requireNonNull(recent30Minutes, "recent30Minutes must not be null");
        Objects.requireNonNull(bucketDuration, "bucketDuration must not be null");
        if (!recent30Minutes.endUtc().equals(queryAtUtc)) {
            throw new IllegalArgumentException("recent30Minutes window must end at queryAtUtc");
        }
        if (baseline != null && !baseline.endUtc().equals(recent30Minutes.startUtc())) {
            throw new IllegalArgumentException("baseline window must end at recent30Minutes startUtc");
        }
    }

    /**
     * 기존 service 호출부와 JSON naming compatibility를 위해 recent 30분 window를 current alias로 제공한다.
     */
    public UtcTimeInterval current() {
        return recent30Minutes;
    }
}
