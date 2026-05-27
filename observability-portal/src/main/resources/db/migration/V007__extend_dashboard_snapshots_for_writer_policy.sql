alter table dashboard_snapshots
  add column primary_rule_id varchar(80),
  add column primary_endpoint_key varchar(240),
  add column max_confidence numeric(4,3);

do $$
begin
  if exists (
    select 1
    from pg_constraint constraint_row
    where constraint_row.contype = 'f'
      and constraint_row.confrelid = 'dashboard_snapshots'::regclass
  ) then
    raise exception 'dashboard_snapshots duplicate cleanup requires no downstream foreign keys';
  end if;
end $$;

with ranked_snapshots as (
  select
    id,
    row_number() over (
      partition by application_id, current_window_end_utc
      order by
        case capture_reason
          when 'state_change' then 1
          when 'high_confidence_concern' then 2
          when 'short_strong_spike' then 3
          when 'query_fallback' then 4
          when 'hourly_scheduled' then 5
          else 6
        end,
        generated_at desc,
        created_at asc,
        id asc
    ) as duplicate_rank
  from dashboard_snapshots
)
delete from dashboard_snapshots snapshot
using ranked_snapshots ranked
where snapshot.id = ranked.id
  and ranked.duplicate_rank > 1;

alter table dashboard_snapshots
  add constraint uk_dashboard_snapshots_application_current_window_end
    unique (application_id, current_window_end_utc);

create index idx_dashboard_snapshots_app_capture_generated
  on dashboard_snapshots (application_id, capture_reason, generated_at desc);

comment on column dashboard_snapshots.capture_reason is
  'writer가 저장한 snapshot capture reason token. null과 legacy/unknown 값은 read-side opaque metadata로 허용한다.';
comment on column dashboard_snapshots.primary_rule_id is
  '저장된 대표 dashboard read model에서 가장 행동 가능한 concern rule id를 복사한 낮은 cardinality helper 값.';
comment on column dashboard_snapshots.primary_endpoint_key is
  '저장된 대표 dashboard read model에서 우선 확인할 endpoint key를 복사한 낮은 cardinality helper 값.';
comment on column dashboard_snapshots.max_confidence is
  '저장된 triage card와 endpoint priority confidence 중 최댓값. confidence-bearing concern이 없으면 null.';
