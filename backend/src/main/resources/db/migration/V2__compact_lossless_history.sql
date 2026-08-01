-- Keep one row only when a team's absolute leaderboard state changes. Successful
-- poll_run rows remain the lossless observation timeline used to reconstruct all
-- unchanged points.

alter table ranking_snapshot drop constraint if exists ranking_snapshot_poll_run_id_fkey;
alter table ranking_snapshot alter column poll_run_id drop not null;
alter table ranking_snapshot
    add constraint ranking_snapshot_poll_run_id_fkey
    foreign key (poll_run_id) references poll_run(id) on delete set null;

with ordered as (
    select id,
           lag(id) over team_history as previous_id,
           lag(team_name) over team_history as previous_team_name,
           lag(unit) over team_history as previous_unit,
           lag(present) over team_history as previous_present,
           lag(ranking) over team_history as previous_ranking,
           lag(take_time) over team_history as previous_take_time,
           lag(commit_times) over team_history as previous_commit_times,
           lag(last_commit_at) over team_history as previous_last_commit_at,
           lag(fastest) over team_history as previous_fastest,
           lag(best_take_time) over team_history as previous_best_take_time
    from ranking_snapshot
    window team_history as (
        partition by contest_id, topic, lower(btrim(team_name))
        order by observed_at, id
    )
), redundant as (
    select snapshot.id
    from ranking_snapshot snapshot
    join ordered on ordered.id = snapshot.id
    where ordered.previous_id is not null
      and snapshot.team_name is not distinct from ordered.previous_team_name
      and snapshot.unit is not distinct from ordered.previous_unit
      and snapshot.present is not distinct from ordered.previous_present
      and snapshot.ranking is not distinct from ordered.previous_ranking
      and snapshot.take_time is not distinct from ordered.previous_take_time
      and snapshot.commit_times is not distinct from ordered.previous_commit_times
      and snapshot.last_commit_at is not distinct from ordered.previous_last_commit_at
      and snapshot.fastest is not distinct from ordered.previous_fastest
      and snapshot.best_take_time is not distinct from ordered.previous_best_take_time
      and not exists (select 1 from anomaly_event anomaly where anomaly.snapshot_id = snapshot.id)
)
delete from ranking_snapshot snapshot
using redundant
where snapshot.id = redundant.id;

create table ranking_current (
    id bigserial primary key,
    snapshot_id bigint not null unique references ranking_snapshot(id) on delete restrict,
    poll_run_id uuid references poll_run(id) on delete set null,
    contest_id varchar(64) not null,
    topic varchar(128) not null,
    team_key varchar(255) not null,
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
    constraint uq_ranking_current_team unique (contest_id, topic, team_key)
);

insert into ranking_current (
    snapshot_id, poll_run_id, contest_id, topic, team_key, team_name, unit,
    present, ranking, take_time, commit_times, last_commit_at, fastest,
    best_take_time, rank_change, take_time_change_pct, commit_delta,
    change_state, observed_at
)
select distinct on (contest_id, topic, lower(btrim(team_name)))
       id, poll_run_id, contest_id, topic, lower(btrim(team_name)), team_name, unit,
       present, ranking, take_time, commit_times, last_commit_at, fastest,
       best_take_time, rank_change, take_time_change_pct, commit_delta,
       change_state, observed_at
from ranking_snapshot
order by contest_id, topic, lower(btrim(team_name)), observed_at desc, id desc;

create index idx_current_contest_topic_rank
    on ranking_current(contest_id, topic, present, ranking);
create index idx_current_team_search
    on ranking_current(contest_id, lower(team_name));
create index idx_poll_run_contest_status_completed
    on poll_run(contest_id, status, completed_at);
