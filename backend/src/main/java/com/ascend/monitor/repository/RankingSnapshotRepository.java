package com.ascend.monitor.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ascend.monitor.domain.RankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    List<RankingSnapshot> findByPollRunId(UUID pollRunId);

    @Query(value = """
            select case
                       when event.poll_run_id = run.id then event.id
                       else -((extract(epoch from run.completed_at) * 1000)::bigint)
                   end as "snapshotId",
                   case when event.poll_run_id = run.id then event.id else null end as "signalSnapshotId",
                   run.completed_at as "observedAt",
                   event.present as "present",
                   event.ranking as "ranking",
                   event.take_time as "takeTime",
                   event.commit_times as "commitTimes",
                   event.best_take_time as "bestTakeTime",
                   case
                       when event.poll_run_id = run.id then event.rank_change
                       when event.present and event.ranking is not null then 0
                       else null
                   end as "rankChange",
                   case
                       when event.poll_run_id = run.id then event.take_time_change_pct
                       when event.present and event.take_time is not null then 0::numeric
                       else null
                   end as "takeTimeChangePct",
                   case
                       when event.poll_run_id = run.id then event.commit_delta
                       when event.present and event.commit_times is null then null
                       else 0
                   end as "commitDelta",
                   case
                       when event.poll_run_id = run.id then event.change_state
                       when event.present then 'UNCHANGED'
                       else 'ABSENT'
                   end as "changeState"
            from poll_run run
            join lateral (
                select snapshot.*
                from ranking_snapshot snapshot
                where snapshot.contest_id = :contestId
                  and snapshot.topic = :topic
                  and lower(btrim(snapshot.team_name)) = lower(btrim(:teamName))
                  and snapshot.observed_at <= run.completed_at
                order by snapshot.observed_at desc, snapshot.id desc
                limit 1
            ) event on true
            where run.contest_id = :contestId
              and run.status = 'SUCCESS'
              and run.completed_at between :from and :to
            order by run.completed_at
            """, nativeQuery = true)
    List<ReconstructedHistoryRow> reconstructHistory(
            @Param("contestId") String contestId,
            @Param("topic") String topic,
            @Param("teamName") String teamName,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Modifying
    @Query(value = """
            with baselines as (
                select distinct on (contest_id, topic, lower(btrim(team_name))) id
                from ranking_snapshot
                where observed_at < :cutoff
                order by contest_id, topic, lower(btrim(team_name)), observed_at desc, id desc
            )
            delete from ranking_snapshot snapshot
            where snapshot.observed_at < :cutoff
              and not exists (
                  select 1 from baselines where baselines.id = snapshot.id
              )
              and not exists (
                  select 1 from ranking_current current_state
                  where current_state.snapshot_id = snapshot.id
              )
            """, nativeQuery = true)
    int deleteObsoleteBefore(@Param("cutoff") Instant cutoff);
}
