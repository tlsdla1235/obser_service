alter table dashboard_snapshots
  drop constraint ck_dashboard_snapshots_state_code;

alter table dashboard_snapshots
  add constraint ck_dashboard_snapshots_state_code
    check (state_code in (
      'waiting_first_data',
      'unknown',
      'idle',
      'active',
      'attention',
      'stale',
      'down',
      'degraded'
    ));
