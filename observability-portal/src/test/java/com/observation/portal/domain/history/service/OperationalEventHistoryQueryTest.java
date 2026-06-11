package com.observation.portal.domain.history.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalEventHistoryQueryTest {

    private static final Instant QUERY_AT = Instant.parse("2026-05-27T13:10:35Z");
    private static final Clock CLOCK = Clock.fixed(QUERY_AT, ZoneOffset.UTC);

    @Test
    void defaultQueryUses24hAndLimit50() {
        OperationalEventHistoryQuery query = OperationalEventHistoryQuery.from(null, null, CLOCK);

        assertThat(query.requestedSince()).isEqualTo("24h");
        assertThat(query.since()).isEqualTo(offset("2026-05-26T13:10:35Z"));
        assertThat(query.until()).isEqualTo(offset("2026-05-27T13:10:35Z"));
        assertThat(query.limit()).isEqualTo(50);
    }

    @Test
    void positiveHourAndDayTokensClampTo14DaysAndLimit100() {
        OperationalEventHistoryQuery thirtyDays = OperationalEventHistoryQuery.from("30d", "999", CLOCK);
        OperationalEventHistoryQuery threeHundredThirtySixHours = OperationalEventHistoryQuery.from("336h", "100", CLOCK);

        assertThat(thirtyDays.requestedSince()).isEqualTo("30d");
        assertThat(thirtyDays.since()).isEqualTo(offset("2026-05-13T13:10:35Z"));
        assertThat(thirtyDays.limit()).isEqualTo(100);
        assertThat(threeHundredThirtySixHours.requestedSince()).isEqualTo("336h");
        assertThat(threeHundredThirtySixHours.since()).isEqualTo(offset("2026-05-13T13:10:35Z"));
        assertThat(threeHundredThirtySixHours.limit()).isEqualTo(100);
    }

    @Test
    void configuredRetentionDaysCanClampBelowFourteenDays() {
        OperationalEventHistoryQuery fourteenDaysWithSevenDayRetention =
                OperationalEventHistoryQuery.from("14d", "50", CLOCK, 7);

        assertThat(fourteenDaysWithSevenDayRetention.requestedSince()).isEqualTo("14d");
        assertThat(fourteenDaysWithSevenDayRetention.since()).isEqualTo(offset("2026-05-20T13:10:35Z"));
    }

    @Test
    void supportsRepresentativeSinceFixturesWithinRetentionBoundary() {
        OperationalEventHistoryQuery twentyFourHours = OperationalEventHistoryQuery.from("24h", "50", CLOCK);
        OperationalEventHistoryQuery sevenDays = OperationalEventHistoryQuery.from("7d", "50", CLOCK);
        OperationalEventHistoryQuery fourteenDays = OperationalEventHistoryQuery.from("14d", "50", CLOCK);
        OperationalEventHistoryQuery thirtyDays = OperationalEventHistoryQuery.from("30d", "50", CLOCK);

        assertThat(twentyFourHours.since()).isEqualTo(offset("2026-05-26T13:10:35Z"));
        assertThat(sevenDays.since()).isEqualTo(offset("2026-05-20T13:10:35Z"));
        assertThat(fourteenDays.since()).isEqualTo(offset("2026-05-13T13:10:35Z"));
        assertThat(thirtyDays.since()).isEqualTo(offset("2026-05-13T13:10:35Z"));
    }

    @Test
    void rejectsBlankMalformedAndNonPositiveSinceOrLimit() {
        assertThatThrownBy(() -> OperationalEventHistoryQuery.from("", "50", CLOCK))
                .isInstanceOf(InvalidOperationalEventHistoryQueryException.class);
        assertThatThrownBy(() -> OperationalEventHistoryQuery.from("abc", "50", CLOCK))
                .isInstanceOf(InvalidOperationalEventHistoryQueryException.class);
        assertThatThrownBy(() -> OperationalEventHistoryQuery.from("0h", "50", CLOCK))
                .isInstanceOf(InvalidOperationalEventHistoryQueryException.class);
        assertThatThrownBy(() -> OperationalEventHistoryQuery.from("-1d", "50", CLOCK))
                .isInstanceOf(InvalidOperationalEventHistoryQueryException.class);
        assertThatThrownBy(() -> OperationalEventHistoryQuery.from("24h", "", CLOCK))
                .isInstanceOf(InvalidOperationalEventHistoryQueryException.class);
        assertThatThrownBy(() -> OperationalEventHistoryQuery.from("24h", "0", CLOCK))
                .isInstanceOf(InvalidOperationalEventHistoryQueryException.class);
        assertThatThrownBy(() -> OperationalEventHistoryQuery.from("24h", "not-a-number", CLOCK))
                .isInstanceOf(InvalidOperationalEventHistoryQueryException.class);
    }

    private static OffsetDateTime offset(String instant) {
        return OffsetDateTime.parse(instant);
    }
}
