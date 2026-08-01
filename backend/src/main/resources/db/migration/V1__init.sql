create table poll_run (
    id uuid primary key,
    contest_id varchar(64) not null,
    contest_name varchar(255),
    status varchar(24) not null,
    started_at timestamptz not null,
    completed_at timestamptz,
    topic_count integer not null default 0,
    snapshot_count integer not null default 0,
    changed_count integer not null default 0,
    anomaly_count integer not null default 0,
    error_message text
);

create index idx_poll_run_status_completed on poll_run(status, completed_at desc);

create table ranking_snapshot (
    id bigserial primary key,
    poll_run_id uuid not null references poll_run(id) on delete cascade,
    contest_id varchar(64) not null,
    topic varchar(128) not null,
    team_name varchar(255) not null,
    unit varchar(255),
    present boolean not null,
    ranking integer,
    take_time numeric(20, 6),
    commit_times integer,
    last_commit_at timestamptz,
    fastest boolean not null default false,
    best_take_time numeric(20, 6),
    rank_change integer,
    take_time_change_pct numeric(12, 4),
    commit_delta integer,
    change_state varchar(24) not null,
    observed_at timestamptz not null,
    constraint uq_snapshot_run_topic_team unique (poll_run_id, topic, team_name)
);

create index idx_snapshot_run_topic_rank on ranking_snapshot(poll_run_id, topic, present, ranking);
create index idx_snapshot_team_history on ranking_snapshot(contest_id, topic, lower(team_name), observed_at);
create index idx_snapshot_observed_at on ranking_snapshot(observed_at desc);

create table anomaly_event (
    id bigserial primary key,
    snapshot_id bigint not null references ranking_snapshot(id) on delete cascade,
    poll_run_id uuid not null references poll_run(id) on delete cascade,
    contest_id varchar(64) not null,
    topic varchar(128) not null,
    team_name varchar(255) not null,
    type varchar(40) not null,
    severity integer not null,
    title varchar(255) not null,
    description text not null,
    previous_take_time numeric(20, 6),
    current_take_time numeric(20, 6),
    baseline_take_time numeric(20, 6),
    detected_at timestamptz not null
);

create index idx_anomaly_detected on anomaly_event(detected_at desc);
create index idx_anomaly_team_topic on anomaly_event(lower(team_name), topic, detected_at desc);
create index idx_anomaly_run_topic on anomaly_event(poll_run_id, topic);
