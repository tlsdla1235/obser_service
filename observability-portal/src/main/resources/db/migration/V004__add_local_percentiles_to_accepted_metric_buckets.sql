alter table accepted_metric_buckets
  add column local_percentiles_json jsonb;

alter table accepted_metric_buckets
  add constraint ck_buckets_local_percentiles_object
    check (local_percentiles_json is null or jsonb_typeof(local_percentiles_json) = 'object');

comment on column accepted_metric_buckets.local_percentiles_json is
  'starter가 보낸 instance_bucket scope의 canonical p95/p99 point. 없으면 null.';
