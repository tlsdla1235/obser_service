-- 2026-06-12 오전 snapshot QA를 위한 임의 seed 데이터다.
-- observability-portal 서버는 내린 상태에서 Postgres 컨테이너에 직접 실행한다.
-- 대상: ecc-test2 프로젝트의 ecc-endpoint-smoke-service / ecc-test2-local-1 인스턴스.

\set ON_ERROR_STOP on

begin;

create or replace function pg_temp.qa_uuid(seed text)
returns uuid
language sql
immutable
as $$
    select (
        substr(md5(seed), 1, 8) || '-' ||
        substr(md5(seed), 9, 4) || '-' ||
        substr(md5(seed), 13, 4) || '-' ||
        substr(md5(seed), 17, 4) || '-' ||
        substr(md5(seed), 21, 12)
    )::uuid;
$$;

create or replace function pg_temp.qa_iso(value timestamptz)
returns text
language sql
stable
as $$
    select to_char(value at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"');
$$;

create or replace function pg_temp.qa_hist(c50 bigint, c100 bigint, c250 bigint, c500 bigint, c1000 bigint)
returns jsonb
language sql
immutable
as $$
    select jsonb_build_array(
        jsonb_build_object('leMs', 50, 'count', c50),
        jsonb_build_object('leMs', 100, 'count', c100),
        jsonb_build_object('leMs', 250, 'count', c250),
        jsonb_build_object('leMs', 500, 'count', c500),
        jsonb_build_object('leMs', 1000, 'count', c1000)
    );
$$;

create or replace function pg_temp.qa_endpoint(
    method text,
    route text,
    request_count bigint,
    error_count bigint,
    c50 bigint,
    c100 bigint,
    c250 bigint,
    c500 bigint,
    c1000 bigint
)
returns jsonb
language sql
immutable
as $$
    select jsonb_build_object(
        'method', method,
        'route', route,
        'requestCount', request_count,
        'errorCount', error_count,
        'durationBuckets', pg_temp.qa_hist(c50, c100, c250, c500, c1000)
    );
$$;

create or replace function pg_temp.qa_endpoint_bucket_json(tag text)
returns jsonb
language sql
immutable
as $$
    select case tag
        when 'healthy' then jsonb_build_array(
            pg_temp.qa_endpoint('GET', '/api/admin/users/{uuid}', 4, 0, 2, 4, 4, 4, 4),
            pg_temp.qa_endpoint('PATCH', '/api/admin/users/level/{requestId}/reject', 3, 0, 1, 2, 3, 3, 3),
            pg_temp.qa_endpoint('GET', '/api/teams/me/regular', 3, 0, 1, 2, 3, 3, 3),
            pg_temp.qa_endpoint('POST', '/api/study/{studyId}/ai-help', 2, 0, 0, 1, 2, 2, 2)
        )
        when 'error_spike' then jsonb_build_array(
            pg_temp.qa_endpoint('GET', '/api/ecc-smoke/error-500', 8, 4, 2, 4, 6, 7, 8),
            pg_temp.qa_endpoint('POST', '/api/admin/setting/semester', 6, 0, 2, 4, 6, 6, 6),
            pg_temp.qa_endpoint('GET', '/api/admin/users/status/{status}', 5, 0, 1, 3, 5, 5, 5),
            pg_temp.qa_endpoint('GET', '/api/admin/users/{uuid}', 5, 0, 1, 3, 5, 5, 5)
        )
        when 'latency_spike' then jsonb_build_array(
            pg_temp.qa_endpoint('GET', '/api/reports/export', 8, 0, 0, 1, 2, 3, 8),
            pg_temp.qa_endpoint('GET', '/api/admin/users/status/{status}', 4, 0, 1, 2, 3, 4, 4),
            pg_temp.qa_endpoint('POST', '/api/study/{studyId}/ai-help', 4, 0, 0, 1, 2, 3, 4),
            pg_temp.qa_endpoint('GET', '/api/teams/me/regular', 4, 0, 1, 2, 3, 4, 4)
        )
        when 'error_and_latency' then jsonb_build_array(
            pg_temp.qa_endpoint('POST', '/api/payments/checkout', 10, 3, 1, 2, 4, 5, 10),
            pg_temp.qa_endpoint('GET', '/api/ecc-smoke/error-500', 6, 2, 1, 2, 3, 5, 6),
            pg_temp.qa_endpoint('GET', '/api/admin/users/{uuid}', 6, 0, 1, 3, 5, 6, 6),
            pg_temp.qa_endpoint('PATCH', '/api/admin/users/level/{requestId}/reject', 6, 0, 1, 3, 5, 6, 6)
        )
        when 'resource_pressure' then jsonb_build_array(
            pg_temp.qa_endpoint('GET', '/api/admin/users/{uuid}', 6, 0, 2, 4, 5, 6, 6),
            pg_temp.qa_endpoint('GET', '/api/reports/export', 4, 0, 0, 1, 1, 2, 4),
            pg_temp.qa_endpoint('PATCH', '/api/admin/users/level/{requestId}/reject', 4, 0, 1, 2, 4, 4, 4),
            pg_temp.qa_endpoint('POST', '/api/admin/setting/semester', 4, 0, 1, 2, 4, 4, 4)
        )
        when 'recovery' then jsonb_build_array(
            pg_temp.qa_endpoint('GET', '/api/admin/users/{uuid}', 5, 0, 2, 4, 5, 5, 5),
            pg_temp.qa_endpoint('GET', '/api/admin/users/status/{status}', 4, 0, 1, 3, 4, 4, 4),
            pg_temp.qa_endpoint('GET', '/api/teams/me/regular', 4, 0, 2, 3, 4, 4, 4),
            pg_temp.qa_endpoint('POST', '/api/study/{studyId}/ai-help', 3, 0, 1, 2, 3, 3, 3)
        )
        else '[]'::jsonb
    end;
$$;

create or replace function pg_temp.qa_priority_item(
    rank int,
    method text,
    route text,
    reason text,
    rule_ids text[],
    confidence numeric,
    score int,
    request_count bigint,
    error_count bigint,
    duration_buckets jsonb,
    slow_share numeric,
    bucket_end_utc timestamptz,
    recommended_action text
)
returns jsonb
language sql
stable
as $$
    select jsonb_build_object(
        'rank', rank,
        'method', method,
        'route', route,
        'endpointKey', method || ' ' || route,
        'reason', reason,
        'ruleIds', to_jsonb(rule_ids),
        'confidence', confidence,
        'score', score,
        'freshness', jsonb_build_object(
            'status', 'current',
            'lastObservedAt', pg_temp.qa_iso(bucket_end_utc),
            'sourceWindow', 'recent_30_minutes',
            'reason', null
        ),
        'evidence', jsonb_build_object(
            'requestCount', request_count,
            'errorCount', error_count,
            'errorRate', round(error_count::numeric / nullif(request_count, 0), 6),
            'baselineRequestCount', null,
            'baselineErrorCount', null,
            'baselineErrorRate', null,
            'errorRateDelta', null,
            'durationBuckets', duration_buckets,
            'baselineDurationBuckets', null,
            'slowShare', slow_share,
            'baselineSlowShare', null,
            'slowShareDelta', null,
            'bucketDistributionSource', 'accepted_bucket',
            'errorEvidenceStatus', 'available',
            'latencyEvidenceStatus', 'available'
        ),
        'recommendedAction', recommended_action
    );
$$;

create or replace function pg_temp.qa_endpoint_priority(tag text, bucket_end_utc timestamptz)
returns jsonb
language sql
stable
as $$
    select case tag
        when 'error_spike' then jsonb_build_array(
            pg_temp.qa_priority_item(
                1, 'GET', '/api/ecc-smoke/error-500', 'error_rate_high',
                array['endpoint_error_rate_high'], 0.92, 92,
                480, 240, pg_temp.qa_hist(120, 240, 360, 420, 480), 0.125000,
                bucket_end_utc, '5xx smoke endpoint의 최근 오류 응답과 공통 예외 로그를 먼저 확인하세요.'
            )
        )
        when 'latency_spike' then jsonb_build_array(
            pg_temp.qa_priority_item(
                1, 'GET', '/api/reports/export', 'latency_slow_share_high',
                array['endpoint_slow_share_high'], 0.86, 86,
                480, 0, pg_temp.qa_hist(0, 60, 120, 180, 480), 0.625000,
                bucket_end_utc, 'export endpoint의 DB query, file generation, downstream call 시간을 먼저 분리하세요.'
            )
        )
        when 'error_and_latency' then jsonb_build_array(
            pg_temp.qa_priority_item(
                1, 'POST', '/api/payments/checkout', 'error_and_latency',
                array['endpoint_error_rate_high', 'endpoint_slow_share_high'], 0.97, 97,
                600, 180, pg_temp.qa_hist(60, 120, 240, 300, 600), 0.500000,
                bucket_end_utc, 'checkout endpoint는 오류와 지연이 동시에 높으므로 transaction 경계와 외부 결제 호출을 함께 확인하세요.'
            ),
            pg_temp.qa_priority_item(
                2, 'GET', '/api/ecc-smoke/error-500', 'error_rate_high',
                array['endpoint_error_rate_high'], 0.84, 84,
                360, 120, pg_temp.qa_hist(60, 120, 180, 300, 360), 0.166667,
                bucket_end_utc, '두 번째 오류 endpoint로 남기되, checkout 원인 확인 후 이어서 봅니다.'
            )
        )
        else '[]'::jsonb
    end;
$$;

create or replace function pg_temp.qa_hist_summary(total_count bigint, buckets jsonb)
returns jsonb
language sql
immutable
as $$
    select jsonb_build_object(
        'status', 'available',
        'totalCount', total_count,
        'buckets', buckets
    );
$$;

create or replace function pg_temp.qa_percentile_point(
    instance_name text,
    bucket_end_utc timestamptz,
    request_count bigint,
    p95_ms bigint,
    p99_ms bigint
)
returns jsonb
language sql
stable
as $$
    select case when request_count > 0 then jsonb_build_object(
        'source', 'starter_canonical_percentile',
        'scope', 'instance_bucket',
        'instance', instance_name,
        'bucketEndUtc', pg_temp.qa_iso(bucket_end_utc),
        'requestCount', request_count,
        'p95Ms', p95_ms,
        'p99Ms', p99_ms
    ) else null end;
$$;

create or replace function pg_temp.qa_triage_evidence(
    request_count bigint,
    error_count bigint,
    error_rate numeric,
    slow_share numeric,
    histogram jsonb,
    cpu_usage numeric,
    heap_usage numeric,
    datasource_usage numeric,
    instance_name text,
    bucket_end_utc timestamptz,
    percentile_request_count bigint,
    p95_ms bigint,
    p99_ms bigint
)
returns jsonb
language sql
stable
as $$
    select jsonb_build_object(
        'requestCount', request_count,
        'currentErrorCount', error_count,
        'currentErrorRate', error_rate,
        'baselineRequestCount', null,
        'baselineErrorCount', null,
        'baselineErrorRate', null,
        'errorRateDelta', null,
        'currentSlowShare', slow_share,
        'baselineSlowShare', null,
        'currentHistogram', case when histogram is null then null else pg_temp.qa_hist_summary(request_count, histogram) end,
        'baselineHistogram', null,
        'runtimeRatio', jsonb_build_object(
            'cpuUsageRatio', cpu_usage,
            'heapUsedRatio', heap_usage,
            'datasourcePoolUsageRatio', datasource_usage
        ),
        'freshnessStatusReason', 'current',
        'sourcePercentilePoint', pg_temp.qa_percentile_point(
            instance_name,
            bucket_end_utc,
            percentile_request_count,
            p95_ms,
            p99_ms
        )
    );
$$;

create or replace function pg_temp.qa_triage_card(
    rule_id text,
    severity text,
    title text,
    summary text,
    recommendation text,
    confidence numeric,
    score int,
    affected_endpoint text,
    evidence jsonb
)
returns jsonb
language sql
immutable
as $$
    select jsonb_build_object(
        'ruleId', rule_id,
        'severity', severity,
        'title', title,
        'summary', summary,
        'recommendation', recommendation,
        'confidence', confidence,
        'score', score,
        'affectedEndpoint', affected_endpoint,
        'evidence', evidence
    );
$$;

create or replace function pg_temp.qa_triage_cards(
    tag text,
    request_count bigint,
    error_count bigint,
    error_rate numeric,
    slow_share numeric,
    histogram jsonb,
    cpu_usage numeric,
    heap_usage numeric,
    datasource_usage numeric,
    instance_name text,
    bucket_end_utc timestamptz,
    percentile_request_count bigint,
    p95_ms bigint,
    p99_ms bigint
)
returns jsonb
language sql
stable
as $$
    select case tag
        when 'error_spike' then jsonb_build_array(
            pg_temp.qa_triage_card(
                'application_error_rate_high',
                'critical',
                'Application 오류율 높음',
                'recent 30 minutes window의 오류율이 기준 이상입니다.',
                '오류율이 높은 endpoint와 공통 예외 로그를 먼저 확인하세요.',
                0.92,
                92,
                'GET /api/ecc-smoke/error-500',
                pg_temp.qa_triage_evidence(request_count, error_count, error_rate, slow_share, histogram, cpu_usage, heap_usage, datasource_usage, instance_name, bucket_end_utc, percentile_request_count, p95_ms, p99_ms)
            )
        )
        when 'latency_spike' then jsonb_build_array(
            pg_temp.qa_triage_card(
                'application_slow_share_high',
                'warning',
                'Application slow share 높음',
                '500ms 초과 응답 비율이 기준 이상입니다.',
                '느린 endpoint의 dependency와 query 시간을 먼저 분리하세요.',
                0.86,
                86,
                'GET /api/reports/export',
                pg_temp.qa_triage_evidence(request_count, error_count, error_rate, slow_share, histogram, cpu_usage, heap_usage, datasource_usage, instance_name, bucket_end_utc, percentile_request_count, p95_ms, p99_ms)
            )
        )
        when 'error_and_latency' then jsonb_build_array(
            pg_temp.qa_triage_card(
                'application_error_and_latency',
                'critical',
                '오류와 지연이 동시에 높음',
                'error rate와 500ms 초과 slow share가 같은 window에서 모두 기준 이상입니다.',
                '공통 원인 가능성이 높은 checkout flow부터 확인하세요.',
                0.97,
                97,
                'POST /api/payments/checkout',
                pg_temp.qa_triage_evidence(request_count, error_count, error_rate, slow_share, histogram, cpu_usage, heap_usage, datasource_usage, instance_name, bucket_end_utc, percentile_request_count, p95_ms, p99_ms)
            )
        )
        when 'resource_pressure' then jsonb_build_array(
            pg_temp.qa_triage_card(
                'datasource_pool_usage_high',
                'critical',
                'Datasource pool 사용률 높음',
                'datasource connection pool usage가 threshold를 넘었습니다.',
                'pool wait, long transaction, slow query를 먼저 확인하세요.',
                0.91,
                91,
                null,
                pg_temp.qa_triage_evidence(request_count, error_count, error_rate, slow_share, histogram, cpu_usage, heap_usage, datasource_usage, instance_name, bucket_end_utc, percentile_request_count, p95_ms, p99_ms)
            ),
            pg_temp.qa_triage_card(
                'cpu_usage_high',
                'warning',
                'CPU 사용률 높음',
                'process CPU usage가 threshold를 넘었습니다.',
                'hot path, batch job, retry loop 여부를 확인하세요.',
                0.82,
                82,
                null,
                pg_temp.qa_triage_evidence(request_count, error_count, error_rate, slow_share, histogram, cpu_usage, heap_usage, datasource_usage, instance_name, bucket_end_utc, percentile_request_count, p95_ms, p99_ms)
            ),
            pg_temp.qa_triage_card(
                'heap_usage_high',
                'warning',
                'Heap 사용률 높음',
                'JVM heap usage가 threshold를 넘었습니다.',
                'GC pressure와 최근 allocation 증가 지점을 확인하세요.',
                0.80,
                80,
                null,
                pg_temp.qa_triage_evidence(request_count, error_count, error_rate, slow_share, histogram, cpu_usage, heap_usage, datasource_usage, instance_name, bucket_end_utc, percentile_request_count, p95_ms, p99_ms)
            )
        )
        when 'stale' then jsonb_build_array(
            pg_temp.qa_triage_card(
                'metric_data_stale',
                'warning',
                'Metric data stale',
                '마지막 accepted bucket 이후 현재 window에 새 metric data가 없습니다.',
                'starter flush와 network path를 확인하세요.',
                0.72,
                72,
                null,
                pg_temp.qa_triage_evidence(null, null, null, null, null, null, null, null, instance_name, bucket_end_utc, 0, null, null)
            )
        )
        when 'down' then jsonb_build_array(
            pg_temp.qa_triage_card(
                'telemetry_down',
                'critical',
                'Telemetry down',
                'metric data와 heartbeat가 모두 장시간 갱신되지 않았습니다.',
                '애플리케이션 프로세스와 starter 설정을 먼저 확인하세요.',
                0.92,
                92,
                null,
                pg_temp.qa_triage_evidence(null, null, null, null, null, null, null, null, instance_name, bucket_end_utc, 0, null, null)
            )
        )
        when 'recovery' then jsonb_build_array(
            pg_temp.qa_triage_card(
                'application_recovery_observed',
                'info',
                'Recovery 관찰됨',
                '직전 degraded/down 이후 현재 window에서는 오류와 지연이 정상 범위입니다.',
                '동일 endpoint 재발 여부를 다음 window까지 관찰하세요.',
                0.65,
                65,
                null,
                pg_temp.qa_triage_evidence(request_count, error_count, error_rate, slow_share, histogram, cpu_usage, heap_usage, datasource_usage, instance_name, bucket_end_utc, percentile_request_count, p95_ms, p99_ms)
            )
        )
        else '[]'::jsonb
    end;
$$;

create or replace function pg_temp.qa_snapshot_endpoint_evidence(priority_items jsonb)
returns jsonb
language sql
stable
as $$
    select jsonb_build_object(
        'source', 'bounded_endpoint_evidence',
        'maxItems', 10,
        'selectionPolicy', 'endpoint_priority_rank_then_high_confidence_concern_then_triage_affected_endpoint',
        'items', coalesce(jsonb_agg(jsonb_build_object(
            'method', item->>'method',
            'route', item->>'route',
            'endpointKey', item->>'endpointKey',
            'rank', nullif(item->>'rank', '')::int,
            'reason', item->>'reason',
            'ruleIds', coalesce(item->'ruleIds', '[]'::jsonb),
            'confidence', nullif(item->>'confidence', '')::numeric,
            'score', nullif(item->>'score', '')::int,
            'requestCount', nullif(item->'evidence'->>'requestCount', '')::bigint,
            'errorRate', nullif(item->'evidence'->>'errorRate', '')::numeric,
            'durationBuckets', item->'evidence'->'durationBuckets',
            'baselineDurationBuckets', item->'evidence'->'baselineDurationBuckets',
            'bucketDistributionSource', item->'evidence'->>'bucketDistributionSource',
            'freshness', item->'freshness',
            'recommendedAction', item->>'recommendedAction'
        ) order by nullif(item->>'rank', '')::int), '[]'::jsonb)
    )
    from jsonb_array_elements(priority_items) as item;
$$;

create or replace function pg_temp.qa_endpoint_refs(priority_items jsonb)
returns jsonb
language sql
stable
as $$
    select coalesce(jsonb_agg(jsonb_build_object(
        'endpointKey', item->>'endpointKey',
        'method', item->>'method',
        'route', item->>'route',
        'relatedApplicationPriorityRank', nullif(item->>'rank', '')::int,
        'relatedRuleIds', coalesce(item->'ruleIds', '[]'::jsonb)
    ) order by nullif(item->>'rank', '')::int), '[]'::jsonb)
    from jsonb_array_elements(priority_items) as item;
$$;

create or replace function pg_temp.qa_zero_insight(tag text)
returns jsonb
language sql
immutable
as $$
    select case tag
        when 'healthy' then jsonb_build_object(
            'reasonCode', 'no_action_needed',
            'message', '현재 window에서 우선 조치가 필요한 metric signal이 없습니다.',
            'recommendedAction', '정상 기준 slot으로 사용하세요.'
        )
        when 'idle' then jsonb_build_object(
            'reasonCode', 'metric_data_idle',
            'message', 'metric freshness는 current지만 traffic이 없는 idle 상태입니다.',
            'recommendedAction', '요청이 들어온 뒤 다시 판단하세요.'
        )
        when 'stale' then jsonb_build_object(
            'reasonCode', 'telemetry_unreachable',
            'message', '최근 window에 새 accepted metric bucket이 없습니다.',
            'recommendedAction', 'starter flush와 네트워크 경로를 확인하세요.'
        )
        when 'down' then jsonb_build_object(
            'reasonCode', 'telemetry_unreachable',
            'message', 'metric data와 heartbeat가 모두 장시간 갱신되지 않았습니다.',
            'recommendedAction', '애플리케이션 프로세스와 starter 설정을 확인하세요.'
        )
        when 'recovery' then jsonb_build_object(
            'reasonCode', 'observing_recovery',
            'message', '현재 window는 정상 범위로 돌아왔고 회복을 관찰 중입니다.',
            'recommendedAction', '다음 snapshot까지 같은 endpoint 재발 여부를 확인하세요.'
        )
        else null
    end;
$$;

create or replace function pg_temp.qa_dashboard_json(
    project_id uuid,
    application_id uuid,
    instance_id uuid,
    application_name text,
    environment text,
    instance_name text,
    tag text,
    slot_end_utc timestamptz,
    last_observed_at timestamptz,
    state_code text,
    state_label text,
    state_rationale text,
    state_action text,
    headline text,
    primary_problem_code text,
    first_look_text text,
    data_quality_state text,
    request_count bigint,
    error_count bigint,
    c50 bigint,
    c100 bigint,
    c250 bigint,
    c500 bigint,
    c1000 bigint,
    cpu_usage numeric,
    heap_usage numeric,
    datasource_usage numeric,
    percentile_request_count bigint,
    p95_ms bigint,
    p99_ms bigint
)
returns jsonb
language plpgsql
stable
as $$
declare
    slot_start_utc timestamptz := slot_end_utc - interval '30 minutes';
    error_rate numeric := case when request_count > 0 then round(error_count::numeric / request_count, 6) else null end;
    slow_count bigint := case when request_count > 0 then greatest(0, c1000 - c500) else 0 end;
    slow_share numeric := case when request_count > 0 then round(greatest(0, c1000 - c500)::numeric / request_count, 6) else null end;
    histogram jsonb := pg_temp.qa_hist(c50, c100, c250, c500, c1000);
    priority_items jsonb := pg_temp.qa_endpoint_priority(tag, slot_end_utc);
    triage_cards jsonb := pg_temp.qa_triage_cards(
        tag,
        request_count,
        error_count,
        error_rate,
        slow_share,
        histogram,
        cpu_usage,
        heap_usage,
        datasource_usage,
        instance_name,
        coalesce(last_observed_at, slot_end_utc),
        percentile_request_count,
        p95_ms,
        p99_ms
    );
    endpoint_refs jsonb := pg_temp.qa_endpoint_refs(priority_items);
    observed_status text := case when last_observed_at >= slot_start_utc and last_observed_at <= slot_end_utc then 'observed' else 'not_observed_in_window' end;
    freshness_label text := case when observed_status = 'observed' then 'current' when tag = 'down' then 'down' else 'stale' end;
    contribution_level text := case
        when jsonb_array_length(priority_items) > 0 then 'contributing'
        when jsonb_array_length(triage_cards) > 0 and tag not in ('healthy', 'idle', 'recovery') then 'attention'
        else 'supporting'
    end;
begin
    return jsonb_build_object(
        'schemaVersion', 'dashboard_read_model.v1',
        'mode', 'snapshot',
        'generatedAt', pg_temp.qa_iso(slot_end_utc + interval '3 seconds'),
        'window', jsonb_build_object(
            'type', 'recent_30_minutes',
            'startUtc', pg_temp.qa_iso(slot_start_utc),
            'endUtc', pg_temp.qa_iso(slot_end_utc)
        ),
        'application', jsonb_build_object(
            'projectId', project_id::text,
            'applicationId', application_id::text,
            'name', application_name,
            'environment', environment,
            'lastAcceptedBucketAt', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at) end,
            'lastHealthyAt', case when tag in ('healthy', 'recovery') then pg_temp.qa_iso(slot_end_utc) else pg_temp.qa_iso(timestamp with time zone '2026-06-12 00:30:00+00') end,
            'sourceWindow', jsonb_build_object(
                'current', jsonb_build_object('startUtc', pg_temp.qa_iso(slot_start_utc), 'endUtc', pg_temp.qa_iso(slot_end_utc)),
                'baseline', null
            ),
            'freshness', jsonb_build_object(
                'lastObservedAt', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at) end,
                'staleAt', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at + interval '2 minutes') end,
                'downAt', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at + interval '10 minutes') end
            )
        ),
        'thresholds', jsonb_build_object(
            'minimumRequestCount', 30,
            'errorRate', 0.05,
            'slowShareOver500ms', 0.20,
            'datasourcePoolUsage', 0.85,
            'cpuUsage', 0.85,
            'heapUsage', 0.90
        ),
        'operatorSummary', jsonb_build_object(
            'headline', headline,
            'primaryProblemCode', primary_problem_code,
            'firstLookText', first_look_text
        ),
        'dataQuality', jsonb_build_object(
            'state', data_quality_state,
            'requestCount', request_count,
            'minimumRequestCount', 30,
            'lastObservedAt', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at) end,
            'limitations', jsonb_build_array(
                'baseline_comparison_not_used_for_mvp',
                'sourceWindow.baseline_null_public_read_model',
                'qa_seed_synthetic_data'
            )
        ),
        'state', jsonb_build_object(
            'code', state_code,
            'label', state_label,
            'rationale', state_rationale,
            'recommendedAction', state_action,
            'scope', 'application'
        ),
        'starterConnection', jsonb_build_object(
            'statusSource', 'starter_heartbeat',
            'lastHeartbeatAt', case when tag in ('stale', 'down') then pg_temp.qa_iso(timestamp with time zone '2026-06-12 03:00:00+00') else pg_temp.qa_iso(slot_end_utc + interval '1 second') end,
            'lastHeartbeatStatus', case when tag = 'down' then 'missing' else 'received' end,
            'connectionMeaning', case when tag = 'down' then 'telemetry_unreachable' else 'heartbeat_received' end,
            'stateImpact', case when tag = 'down' then 'supports_down_state' else 'none' end
        ),
        'signals', jsonb_build_object(
            'red', jsonb_build_object(
                'requestCount', request_count,
                'errorCount', error_count,
                'errorSemantic', 'server_error_5xx',
                'errorRate', error_rate,
                'slowCountOver500ms', case when request_count > 0 then slow_count else null end,
                'slowShareOver500ms', slow_share,
                'latencyEvidenceStatus', case when request_count > 0 then 'available' else 'insufficient' end
            ),
            'use', jsonb_build_object(
                'datasourcePoolUsage', jsonb_build_object(
                    'max', datasource_usage,
                    'threshold', 0.85,
                    'status', case when datasource_usage is null then 'missing' when datasource_usage >= 0.85 then 'high' else 'normal' end,
                    'observedAt', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at) end
                ),
                'cpuUsage', jsonb_build_object(
                    'max', cpu_usage,
                    'threshold', 0.85,
                    'status', case when cpu_usage is null then 'missing' when cpu_usage >= 0.85 then 'high' else 'normal' end,
                    'observedAt', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at) end
                ),
                'heapUsage', jsonb_build_object(
                    'max', heap_usage,
                    'threshold', 0.90,
                    'status', case when heap_usage is null then 'missing' when heap_usage >= 0.90 then 'high' else 'normal' end,
                    'observedAt', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at) end
                )
            )
        ),
        'stateReasons', jsonb_build_array(jsonb_build_object(
            'type', 'state_decision',
            'severity', case when state_code in ('down', 'degraded') then 'warning' else 'info' end,
            'scope', 'application',
            'target', null,
            'reasonCode', coalesce(primary_problem_code, tag),
            'operatorText', state_rationale
        )),
        'attentionEvidence', case when jsonb_array_length(triage_cards) > 0 then jsonb_build_array(jsonb_build_object(
            'type', 'triage_card',
            'severity', triage_cards->0->>'severity',
            'scope', 'application',
            'target', triage_cards->0->>'affectedEndpoint',
            'reasonCode', triage_cards->0->>'ruleId',
            'operatorText', triage_cards->0->>'summary',
            'affectsLifecycleState', false
        )) else '[]'::jsonb end,
        'firstLookCandidates', case when primary_problem_code is null then '[]'::jsonb else jsonb_build_array(jsonb_build_object(
            'rank', 1,
            'type', case when jsonb_array_length(priority_items) > 0 then 'endpoint' else 'application' end,
            'target', coalesce(priority_items->0->>'endpointKey', null),
            'reasonCode', primary_problem_code,
            'source', case when jsonb_array_length(priority_items) > 0 then 'endpointPriority' else 'triageCards' end,
            'operatorText', first_look_text
        )) end,
        'readSemantics', jsonb_build_object(
            'source', 'dashboard_snapshots.read_model_json',
            'snapshotDetailRecalculates', false,
            'markerIsStateSource', false,
            'baselineComparisonUsedForMvpDecision', false,
            'helperColumnsAreStateSource', false,
            'histogramBucketsUsedForPercentiles', false,
            'bucketDistributionSource', 'accepted_bucket',
            'bucketDistributionMeaning', 'stored_read_model.accepted_metric_buckets.duration_buckets_json_distribution_display_only',
            'bucketEndBoundary', 'bucket_end_utc > window.startUtc and bucket_end_utc <= window.endUtc'
        ),
        'zeroInsight', pg_temp.qa_zero_insight(tag),
        'recovery', jsonb_build_object(
            'isRecovering', tag = 'recovery',
            'lastHealthyAt', case when tag in ('healthy', 'recovery') then pg_temp.qa_iso(slot_end_utc) else pg_temp.qa_iso(timestamp with time zone '2026-06-12 00:30:00+00') end,
            'retryAfterSeconds', case when tag = 'recovery' then 1800 else null end,
            'recommendedAction', case when tag = 'recovery' then '다음 30분 snapshot에서 같은 endpoint 재발 여부를 확인하세요.' else null end
        ),
        'metrics', jsonb_build_object(
            'requestCount', request_count,
            'errorCount', error_count,
            'errorRate', error_rate
        ),
        'sourceScopedPercentiles', jsonb_build_object(
            'source', 'starter_canonical_percentile',
            'scope', 'instance_bucket',
            'displayPolicy', 'source_scoped_points',
            'aggregatePolicy', 'no_average_no_max_no_merge_no_histogram_recalculation',
            'status', case when percentile_request_count > 0 then 'available' else 'insufficient' end,
            'reason', case when percentile_request_count > 0 then null else 'no_valid_percentile_points_in_recent_30_minutes' end,
            'items', case when percentile_request_count > 0 then jsonb_build_array(jsonb_build_object(
                'source', 'starter_canonical_percentile',
                'application', application_name,
                'environment', environment,
                'instance', instance_name,
                'bucketStartUtc', pg_temp.qa_iso(slot_end_utc - interval '30 seconds'),
                'bucketEndUtc', pg_temp.qa_iso(slot_end_utc),
                'requestCount', percentile_request_count,
                'p95Ms', p95_ms,
                'p99Ms', p99_ms
            )) else '[]'::jsonb end
        ),
        'histogramDistribution', jsonb_build_object(
            'source', 'accepted_bucket',
            'scope', 'application',
            'displayPolicy', 'cumulative_bucket_distribution',
            'aggregatePolicy', 'display_bucket_only_no_percentile_recalculation',
            'current', jsonb_build_object(
                'status', case when request_count > 0 then 'available' else 'insufficient' end,
                'reason', case when request_count > 0 then null else 'no_request_count_in_window' end,
                'totalCount', request_count,
                'buckets', histogram
            ),
            'baseline', jsonb_build_object(
                'status', 'unavailable',
                'reason', 'baseline_comparison_not_used_for_mvp',
                'totalCount', 0,
                'buckets', jsonb_build_array()
            )
        ),
        'triageCards', triage_cards,
        'endpointPriority', priority_items,
        'instances', jsonb_build_array(jsonb_build_object(
            'instanceId', instance_id::text,
            'instanceName', instance_name,
            'lastSeenAt', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at) end,
            'summary', jsonb_build_object(
                'observationStatus', jsonb_build_object(
                    'code', observed_status,
                    'reason', case when observed_status = 'observed' then 'selected_instance_metric_bucket_observed' else 'selected_instance_metric_bucket_not_observed' end,
                    'lastObservedBucketEndUtc', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at) end
                ),
                'starterConnection', jsonb_build_object(
                    'lastHeartbeatAt', case when tag in ('stale', 'down') then pg_temp.qa_iso(timestamp with time zone '2026-06-12 03:00:00+00') else pg_temp.qa_iso(slot_end_utc + interval '1 second') end,
                    'lastHeartbeatStatus', case when tag = 'down' then 'missing' else 'received' end,
                    'freshnessLabel', case when tag = 'down' then 'down' else freshness_label end
                ),
                'red', jsonb_build_object(
                    'requestCount', request_count,
                    'slowCountOver500ms', case when request_count > 0 then slow_count else null end,
                    'slowShareOver500ms', slow_share
                ),
                'applicationContribution', jsonb_build_object(
                    'level', contribution_level,
                    'reason', coalesce(primary_problem_code, 'no_action_needed')
                )
            ),
            'links', jsonb_build_object(
                'evidence', '/api/projects/' || project_id || '/applications/' || application_id || '/instances/' || instance_id || '/evidence'
            )
        )),
        'snapshot', null,
        'snapshotEndpointEvidence', pg_temp.qa_snapshot_endpoint_evidence(priority_items),
        'instanceSummary', jsonb_build_object(
            'schemaVersion', '1.0',
            'source', 'bounded_instance_summary',
            'maxItems', 50,
            'selectionPolicy', 'triage_contributors_then_freshness_attention_then_high_request_count',
            'items', jsonb_build_array(jsonb_build_object(
                'instanceId', instance_id::text,
                'instanceName', instance_name,
                'observationStatus', observed_status,
                'metricData', jsonb_build_object(
                    'statusSource', 'accepted_bucket',
                    'lastAcceptedBucketAt', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at) end,
                    'freshnessLabel', freshness_label
                ),
                'starterConnection', jsonb_build_object(
                    'statusSource', 'starter_heartbeat',
                    'lastHeartbeatAt', case when tag in ('stale', 'down') then pg_temp.qa_iso(timestamp with time zone '2026-06-12 03:00:00+00') else pg_temp.qa_iso(slot_end_utc + interval '1 second') end,
                    'lastHeartbeatStatus', case when tag = 'down' then 'missing' else 'received' end,
                    'connectionMeaning', case when tag = 'down' then 'telemetry_unreachable' else 'heartbeat_received' end,
                    'stateImpact', case when tag = 'down' then 'supports_down_state' else 'none' end
                ),
                'starterPercentilePoint', case when percentile_request_count > 0 then jsonb_build_object(
                    'source', 'starter_canonical_percentile',
                    'scope', 'instance_bucket',
                    'bucketStartUtc', pg_temp.qa_iso(slot_end_utc - interval '30 seconds'),
                    'bucketEndUtc', pg_temp.qa_iso(slot_end_utc),
                    'requestCount', percentile_request_count,
                    'p95Ms', p95_ms,
                    'p99Ms', p99_ms
                ) else null end,
                'resourceHints', jsonb_build_object(
                    'source', 'accepted_bucket_latest_sample',
                    'status', case when last_observed_at is null then 'unavailable' else 'available' end,
                    'bucketEndUtc', case when last_observed_at is null then null else pg_temp.qa_iso(last_observed_at) end,
                    'cpuUsageRatio', cpu_usage,
                    'heapUsedRatio', heap_usage,
                    'datasourcePoolUsageRatio', datasource_usage
                ),
                'applicationTriageContribution', jsonb_build_object(
                    'status', case when jsonb_array_length(triage_cards) > 0 then 'available' else 'none' end,
                    'contributed', jsonb_array_length(triage_cards) > 0,
                    'reason', coalesce(primary_problem_code, 'no_action_needed'),
                    'relatedRuleIds', coalesce((
                        select jsonb_agg(card->>'ruleId')
                        from jsonb_array_elements(triage_cards) as card
                    ), '[]'::jsonb)
                ),
                'endpointEvidenceRefs', endpoint_refs
            ))
        )
    );
end;
$$;

with target as (
    select
        p.id as project_id,
        a.id as application_id,
        i.id as instance_id,
        a.name as application_name,
        a.environment,
        i.instance_name
    from projects p
    join applications a on a.project_id = p.id
    join application_instances i on i.application_id = a.id
    where p.id = '2627f210-f80d-46cd-aa39-114d9b5fe140'::uuid
      and a.id = 'a45e18f9-fc87-4b19-a99e-77423dacacb0'::uuid
      and i.id = '606e761a-5cf5-4935-924e-0e6dd5a51033'::uuid
),
scenarios as (
    select *
    from (values
        ('healthy', timestamptz '2026-06-12 00:30:00+00', true, 'active', 'state_change', null, null, null::numeric, 'Active', '정상 traffic과 freshness가 함께 관찰됩니다.', '기준 정상 slot로 보고 이후 anomaly slot과 비교하세요.', '정상 traffic 관찰 중', null, '정상 기준 slot입니다.', 'complete', 12, 0, 4, 8, 11, 12, 12, 0.18000, 0.31000, 0.22000, 95, 140),
        ('error_spike', timestamptz '2026-06-12 01:00:00+00', true, 'degraded', 'state_change', 'application_error_rate_high', 'GET /api/ecc-smoke/error-500', 0.920, 'Degraded', '오류율이 threshold를 넘고 endpoint error spike가 관찰됩니다.', '5xx endpoint와 예외 로그를 먼저 확인하세요.', '오류율이 높은 window입니다.', 'application_error_rate_high', 'GET /api/ecc-smoke/error-500부터 확인하세요.', 'complete', 24, 4, 5, 11, 20, 23, 24, 0.26000, 0.39000, 0.31000, 180, 300),
        ('latency_spike', timestamptz '2026-06-12 01:30:00+00', true, 'degraded', 'scheduled_snapshot', 'application_slow_share_high', 'GET /api/reports/export', 0.860, 'Degraded', '500ms 초과 slow share가 threshold를 넘습니다.', '느린 endpoint의 DB query와 downstream 시간을 분리하세요.', '지연 비율이 높은 window입니다.', 'application_slow_share_high', 'GET /api/reports/export latency부터 확인하세요.', 'complete', 20, 0, 2, 5, 8, 11, 20, 0.32000, 0.51000, 0.44000, 850, 980),
        ('error_and_latency', timestamptz '2026-06-12 02:00:00+00', true, 'degraded', 'state_change', 'application_error_and_latency', 'POST /api/payments/checkout', 0.970, 'Degraded', '오류와 지연이 같은 window에서 함께 기준을 넘습니다.', 'checkout flow의 transaction과 외부 호출을 함께 확인하세요.', '오류와 지연이 동시에 높은 window입니다.', 'application_error_and_latency', 'POST /api/payments/checkout부터 확인하세요.', 'complete', 28, 5, 2, 5, 11, 15, 28, 0.41000, 0.62000, 0.58000, 900, 1000),
        ('resource_pressure', timestamptz '2026-06-12 02:30:00+00', true, 'degraded', 'scheduled_snapshot', 'datasource_pool_usage_high', null, 0.910, 'Degraded', 'USE signal에서 datasource/cpu/heap pressure가 동시에 관찰됩니다.', 'pool wait, slow query, hot path를 순서대로 확인하세요.', 'resource pressure가 높은 window입니다.', 'datasource_pool_usage_high', 'datasource pool usage와 CPU/heap hint를 먼저 확인하세요.', 'complete', 18, 0, 4, 9, 15, 17, 18, 0.91000, 0.93000, 0.94000, 320, 600),
        ('idle', timestamptz '2026-06-12 03:00:00+00', true, 'idle', 'scheduled_snapshot', null, null, null::numeric, 'Metric data idle', 'Freshness는 current지만 traffic이 idle 상태라 anomaly 판단을 보류합니다.', '요청이 들어온 뒤 metric state를 다시 평가하세요.', 'traffic이 없는 idle window입니다.', null, 'idle 상태입니다.', 'sample_limited', 0, 0, 0, 0, 0, 0, 0, 0.12000, 0.28000, 0.18000, null::bigint, null::bigint),
        ('stale', timestamptz '2026-06-12 03:30:00+00', false, 'stale', 'state_change', 'metric_data_stale', null, 0.720, 'Metric data stale', '마지막 accepted bucket 이후 새 metric data가 도착하지 않았습니다.', 'starter flush와 network path를 확인하세요.', 'stale window입니다.', 'metric_data_stale', 'metric freshness와 starter flush를 확인하세요.', 'stale', 0, 0, 0, 0, 0, 0, 0, null::numeric, null::numeric, null::numeric, null::bigint, null::bigint),
        ('down', timestamptz '2026-06-12 04:00:00+00', false, 'down', 'state_change', 'telemetry_down', null, 0.920, 'Telemetry down', 'metric data와 heartbeat가 모두 장시간 갱신되지 않았습니다.', '애플리케이션 프로세스와 starter 설정을 확인하세요.', 'down window입니다.', 'telemetry_down', '프로세스/네트워크/starter 설정을 먼저 확인하세요.', 'unavailable', 0, 0, 0, 0, 0, 0, 0, null::numeric, null::numeric, null::numeric, null::bigint, null::bigint),
        ('recovery', timestamptz '2026-06-12 04:30:00+00', true, 'active', 'state_change', 'application_recovery_observed', null, 0.650, 'Active recovery', '직전 degraded/down 이후 현재 window는 정상 범위로 회복되었습니다.', '다음 snapshot까지 재발 여부를 관찰하세요.', '회복이 관찰된 window입니다.', 'application_recovery_observed', '회복 후 재발 여부를 관찰하세요.', 'complete', 16, 0, 6, 12, 16, 16, 16, 0.19000, 0.33000, 0.24000, 160, 220)
    ) as scenario(
        tag,
        slot_end_utc,
        has_metric_buckets,
        state_code,
        capture_reason,
        primary_rule_id,
        primary_endpoint_key,
        max_confidence,
        state_label,
        state_rationale,
        state_action,
        headline,
        primary_problem_code,
        first_look_text,
        data_quality_state,
        request_per_bucket,
        error_per_bucket,
        c50_per_bucket,
        c100_per_bucket,
        c250_per_bucket,
        c500_per_bucket,
        c1000_per_bucket,
        cpu_usage,
        heap_usage,
        datasource_usage,
        p95_ms,
        p99_ms
    )
),
bucket_rows as (
    select
        target.*,
        scenarios.*,
        bucket_start_utc,
        bucket_start_utc + interval '30 seconds' as bucket_end_utc
    from target
    cross join scenarios
    cross join lateral generate_series(
        scenarios.slot_end_utc - interval '30 minutes',
        scenarios.slot_end_utc - interval '30 seconds',
        interval '30 seconds'
    ) as bucket_start_utc
    where scenarios.has_metric_buckets
),
upserted_buckets as (
    insert into accepted_metric_buckets (
        id,
        project_id,
        application_id,
        application_instance_id,
        schema_version,
        idempotency_key,
        payload_hash,
        bucket_start_utc,
        bucket_end_utc,
        duration_seconds,
        accepted_at,
        request_count,
        error_count,
        duration_buckets_json,
        cpu_usage_ratio,
        heap_used_ratio,
        datasource_pool_usage_ratio,
        endpoints_json,
        created_at,
        local_percentiles_json
    )
    select
        pg_temp.qa_uuid('2026-06-12-rule-qa-bucket:' || instance_id || ':' || bucket_start_utc::text),
        project_id,
        application_id,
        instance_id,
        '1.0',
        'qa-rule-20260612-' || tag || '-' || to_char(bucket_start_utc at time zone 'UTC', 'YYYYMMDDHH24MISS'),
        md5('qa-rule-20260612-' || tag || '-' || bucket_start_utc::text),
        bucket_start_utc,
        bucket_end_utc,
        30,
        bucket_end_utc + interval '2 seconds',
        request_per_bucket,
        error_per_bucket,
        pg_temp.qa_hist(c50_per_bucket, c100_per_bucket, c250_per_bucket, c500_per_bucket, c1000_per_bucket),
        cpu_usage,
        heap_usage,
        datasource_usage,
        pg_temp.qa_endpoint_bucket_json(tag),
        bucket_end_utc + interval '2 seconds',
        case when request_per_bucket > 0 then jsonb_build_object(
            'source', 'starter_local',
            'scope', 'instance_bucket',
            'mergeable', false,
            'bucketStartUtc', pg_temp.qa_iso(bucket_start_utc),
            'bucketEndUtc', pg_temp.qa_iso(bucket_end_utc),
            'requestCount', request_per_bucket,
            'p95Ms', p95_ms,
            'p99Ms', p99_ms
        ) else null end
    from bucket_rows
    on conflict on constraint uk_buckets_instance_bucket_start
    do update set
        project_id = excluded.project_id,
        application_id = excluded.application_id,
        schema_version = excluded.schema_version,
        idempotency_key = excluded.idempotency_key,
        payload_hash = excluded.payload_hash,
        bucket_end_utc = excluded.bucket_end_utc,
        duration_seconds = excluded.duration_seconds,
        accepted_at = excluded.accepted_at,
        request_count = excluded.request_count,
        error_count = excluded.error_count,
        duration_buckets_json = excluded.duration_buckets_json,
        cpu_usage_ratio = excluded.cpu_usage_ratio,
        heap_used_ratio = excluded.heap_used_ratio,
        datasource_pool_usage_ratio = excluded.datasource_pool_usage_ratio,
        endpoints_json = excluded.endpoints_json,
        local_percentiles_json = excluded.local_percentiles_json
    returning id
),
snapshot_rows as (
    select
        target.*,
        scenarios.*,
        scenarios.slot_end_utc - interval '30 minutes' as window_start_utc,
        case
            when scenarios.has_metric_buckets then scenarios.slot_end_utc
            else timestamptz '2026-06-12 03:00:00+00'
        end as last_observed_at,
        scenarios.request_per_bucket * 60 as request_count,
        scenarios.error_per_bucket * 60 as error_count,
        scenarios.c50_per_bucket * 60 as c50,
        scenarios.c100_per_bucket * 60 as c100,
        scenarios.c250_per_bucket * 60 as c250,
        scenarios.c500_per_bucket * 60 as c500,
        scenarios.c1000_per_bucket * 60 as c1000
    from target
    cross join scenarios
),
upserted_snapshots as (
    insert into dashboard_snapshots (
        id,
        project_id,
        application_id,
        generated_at,
        current_window_start_utc,
        current_window_end_utc,
        baseline_window_start_utc,
        baseline_window_end_utc,
        last_accepted_ingest_at,
        last_observed_at,
        state_code,
        capture_reason,
        read_model_json,
        created_at,
        primary_rule_id,
        primary_endpoint_key,
        max_confidence
    )
    select
        pg_temp.qa_uuid('2026-06-12-rule-qa-snapshot:' || application_id || ':' || slot_end_utc::text),
        project_id,
        application_id,
        slot_end_utc + interval '3 seconds',
        window_start_utc,
        slot_end_utc,
        window_start_utc - interval '24 hours',
        slot_end_utc - interval '24 hours',
        case when last_observed_at is null then null else last_observed_at + interval '2 seconds' end,
        last_observed_at,
        state_code,
        capture_reason,
        pg_temp.qa_dashboard_json(
            project_id,
            application_id,
            instance_id,
            application_name,
            environment,
            instance_name,
            tag,
            slot_end_utc,
            last_observed_at,
            state_code,
            state_label,
            state_rationale,
            state_action,
            headline,
            primary_problem_code,
            first_look_text,
            data_quality_state,
            request_count,
            error_count,
            c50,
            c100,
            c250,
            c500,
            c1000,
            cpu_usage,
            heap_usage,
            datasource_usage,
            request_per_bucket,
            p95_ms,
            p99_ms
        ),
        slot_end_utc + interval '3 seconds',
        primary_rule_id,
        primary_endpoint_key,
        max_confidence
    from snapshot_rows
    on conflict on constraint uk_dashboard_snapshots_application_current_window_end
    do update set
        project_id = excluded.project_id,
        generated_at = excluded.generated_at,
        current_window_start_utc = excluded.current_window_start_utc,
        baseline_window_start_utc = excluded.baseline_window_start_utc,
        baseline_window_end_utc = excluded.baseline_window_end_utc,
        last_accepted_ingest_at = excluded.last_accepted_ingest_at,
        last_observed_at = excluded.last_observed_at,
        state_code = excluded.state_code,
        capture_reason = excluded.capture_reason,
        read_model_json = excluded.read_model_json,
        primary_rule_id = excluded.primary_rule_id,
        primary_endpoint_key = excluded.primary_endpoint_key,
        max_confidence = excluded.max_confidence
    returning id
)
select
    (select count(*) from upserted_buckets) as accepted_bucket_rows,
    (select count(*) from upserted_snapshots) as dashboard_snapshot_rows;

commit;
