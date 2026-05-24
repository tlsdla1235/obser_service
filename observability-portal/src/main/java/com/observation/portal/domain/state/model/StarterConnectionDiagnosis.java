package com.observation.portal.domain.state.model;

/**
 * starter connection 축과 accepted bucket axis 조합에서 read model에 전달할 diagnosis 후보 코드다.
 */
public enum StarterConnectionDiagnosis {
    STARTER_CONNECTED("starter_connected"),
    STARTER_CONNECTED_BUT_NO_ACCEPTED_BUCKET("starter_connected_but_no_accepted_bucket"),
    NO_RECENT_TRAFFIC("no_recent_traffic"),
    METRIC_DATA_IDLE("metric_data_idle"),
    STARTER_CONNECTION_STALE("starter_connection_stale"),
    TELEMETRY_UNREACHABLE("telemetry_unreachable"),
    UNKNOWN("unknown");

    private final String code;

    StarterConnectionDiagnosis(String code) {
        this.code = code;
    }

    /**
     * 외부 read model에 싣기 좋은 snake_case diagnosis 코드를 반환한다.
     */
    public String code() {
        return code;
    }
}
