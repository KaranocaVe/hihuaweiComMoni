package com.ascend.monitor.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.ascend.monitor.domain.AnomalyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnomalyEventRepository extends JpaRepository<AnomalyEvent, Long>, JpaSpecificationExecutor<AnomalyEvent> {

    List<AnomalyEvent> findByPollRunId(UUID pollRunId);

    List<AnomalyEvent> findBySnapshotIdIn(Collection<Long> snapshotIds);

    long countByDetectedAtAfter(Instant cutoff);

    @Query(value = """
            with signals as (
                select lower(btrim(team_name)) as team_key,
                       min(team_name) as team_name,
                       count(*) as signal_count,
                       count(distinct topic) as topic_count,
                       count(*) filter (where type = 'POSSIBLE_HIDING') as hiding_count,
                       count(*) filter (where type = 'SCORE_REGRESSION') as regression_count,
                       count(*) filter (where type = 'SCORE_REBOUND') as rebound_count,
                       count(*) filter (where type = 'DROPPED_FROM_BOARD') as dropped_count,
                       count(*) filter (where type = 'RETURNED_TO_BOARD') as returned_count,
                       avg(severity) as average_severity,
                       max(detected_at) as last_detected_at
                from anomaly_event
                where contest_id = :contestId
                  and detected_at >= :since
                group by lower(btrim(team_name))
            ), team_quality as (
                select lower(btrim(team_name)) as team_key,
                       topic,
                       min(best_take_time) filter (where best_take_time > 0) as team_best_time
                from ranking_snapshot
                where contest_id = :contestId
                group by lower(btrim(team_name)), topic
            ), topic_benchmarks as (
                select topic,
                       min(take_time) filter (where take_time > 0) as topic_best_time
                from ranking_current
                where contest_id = :contestId
                  and present
                group by topic
            ), state_history as (
                select lower(btrim(ranking_snapshot.team_name)) as team_key,
                       ranking_snapshot.team_name,
                       ranking_snapshot.topic,
                       observed_at,
                       id,
                       take_time,
                       best_take_time,
                       commit_delta,
                       take_time_change_pct,
                       lag(take_time) over history as previous_take_time,
                       lag(best_take_time) over history as previous_best_take_time,
                       quality.team_best_time,
                       benchmark.topic_best_time
                from ranking_snapshot
                left join team_quality quality
                  on quality.team_key = lower(btrim(ranking_snapshot.team_name))
                 and quality.topic = ranking_snapshot.topic
                left join topic_benchmarks benchmark
                  on benchmark.topic = ranking_snapshot.topic
                where ranking_snapshot.contest_id = :contestId
                window history as (
                    partition by ranking_snapshot.topic, lower(btrim(ranking_snapshot.team_name))
                    order by ranking_snapshot.observed_at, ranking_snapshot.id
                )
            ), state_gaps as (
                select *,
                       case when take_time is not null and best_take_time > 0
                            then 100 * (take_time - best_take_time) / best_take_time end as current_gap,
                       case when previous_take_time is not null and previous_best_take_time > 0
                            then 100 * (previous_take_time - previous_best_take_time)
                                 / previous_best_take_time end as previous_gap
                from state_history
            ), activity as (
                select team_key,
                       count(*) filter (where commit_delta > 0) as submission_rounds,
                       count(distinct topic) filter (where commit_delta > 0) as active_topic_count,
                       coalesce(sum(greatest(commit_delta, 0)), 0) as commit_count,
                       count(*) filter (where commit_delta > 0 and take_time is null) as scoreless_submissions,
                       count(*) filter (where previous_take_time is null and take_time is not null) as scoreless_returns
                from state_gaps
                where observed_at >= :since
                group by team_key
            ), episodes as (
                select state.team_key,
                       state.observed_at as hidden_at,
                       recovery.recovered_at,
                       case when recovery.recovered_at is not null
                            then extract(epoch from (recovery.recovered_at - state.observed_at)) / 3600.0 end
                            as recovery_hours
                from state_gaps state
                left join lateral (
                    select next_state.observed_at as recovered_at
                    from state_gaps next_state
                    where next_state.team_key = state.team_key
                      and next_state.topic = state.topic
                      and next_state.observed_at > state.observed_at
                      and next_state.observed_at <= state.observed_at + interval '12 hours'
                      and next_state.take_time is not null
                      and next_state.current_gap <= least(:nearBestPercent * 2, :hidingPercent / 2)
                      and next_state.take_time < state.take_time
                    order by next_state.observed_at, next_state.id
                    limit 1
                ) recovery on true
                where state.observed_at >= :since
                  and state.commit_delta > 0
                  and state.current_gap >= :hidingPercent
                  and state.team_best_time is not null
                  and state.topic_best_time is not null
                  and state.team_best_time <= state.topic_best_time * :hidingMaxBestTimeRatio
                  and (state.previous_gap is null or state.previous_gap < :hidingPercent)
            ), episode_stats as (
                select team_key,
                       count(*) as hiding_entries,
                       count(*) filter (where recovered_at is not null) as recoveries,
                       count(*) filter (where recovery_hours >= 2) as long_hold_recoveries,
                       count(*) filter (where recovery_hours >= 4) as very_long_recoveries,
                       percentile_cont(0.5) within group (order by recovery_hours)
                           filter (where recovered_at is not null) as median_recovery_hours
                from episodes
                group by team_key
            )
            select signals.team_key as "teamKey",
                   signals.team_name as "teamName",
                   signals.signal_count as "signalCount",
                   signals.topic_count as "topicCount",
                   signals.hiding_count as "hidingCount",
                   signals.regression_count as "regressionCount",
                   signals.rebound_count as "reboundCount",
                   signals.dropped_count as "droppedCount",
                   signals.returned_count as "returnedCount",
                   signals.average_severity as "averageSeverity",
                   signals.last_detected_at as "lastDetectedAt",
                   coalesce(activity.submission_rounds, 0) as "submissionRounds",
                   coalesce(activity.active_topic_count, 0) as "activeTopicCount",
                   coalesce(activity.commit_count, 0) as "commitCount",
                   coalesce(activity.scoreless_submissions, 0) as "scorelessSubmissions",
                   coalesce(activity.scoreless_returns, 0) as "scorelessReturns",
                   coalesce(episode_stats.hiding_entries, 0) as "hidingEntries",
                   coalesce(episode_stats.recoveries, 0) as "recoveries",
                   coalesce(episode_stats.long_hold_recoveries, 0) as "longHoldRecoveries",
                   coalesce(episode_stats.very_long_recoveries, 0) as "veryLongRecoveries",
                   episode_stats.median_recovery_hours as "medianRecoveryHours"
            from signals
            left join activity on activity.team_key = signals.team_key
            left join episode_stats on episode_stats.team_key = signals.team_key
            """, nativeQuery = true)
    List<TeamAnomalySummary> summarizeTeamBehavior(@Param("contestId") String contestId,
                                                   @Param("since") Instant since,
                                                   @Param("hidingPercent") double hidingPercent,
                                                   @Param("nearBestPercent") double nearBestPercent,
                                                   @Param("hidingMaxBestTimeRatio") double hidingMaxBestTimeRatio);

    @Modifying
    int deleteByDetectedAtBefore(Instant cutoff);
}
