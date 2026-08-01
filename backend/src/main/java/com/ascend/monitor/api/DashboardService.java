package com.ascend.monitor.api;

import static com.ascend.monitor.api.ApiModels.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ascend.monitor.config.MonitorProperties;
import com.ascend.monitor.domain.AnomalyEvent;
import com.ascend.monitor.domain.AnomalyType;
import com.ascend.monitor.domain.ChangeState;
import com.ascend.monitor.domain.PollRun;
import com.ascend.monitor.domain.PollStatus;
import com.ascend.monitor.domain.RankingSnapshot;
import com.ascend.monitor.polling.LeaderboardPoller;
import com.ascend.monitor.repository.AnomalyEventRepository;
import com.ascend.monitor.repository.PollRunRepository;
import com.ascend.monitor.repository.RankingSnapshotRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final String DISCLAIMER = "异常信号来自公开榜单的时间序列变化，只用于辅助核查，不构成对参赛者违规行为的认定。";

    private final MonitorProperties properties;
    private final LeaderboardPoller poller;
    private final PollRunRepository pollRunRepository;
    private final RankingSnapshotRepository snapshotRepository;
    private final AnomalyEventRepository anomalyRepository;

    public DashboardService(MonitorProperties properties, LeaderboardPoller poller,
                            PollRunRepository pollRunRepository,
                            RankingSnapshotRepository snapshotRepository,
                            AnomalyEventRepository anomalyRepository) {
        this.properties = properties;
        this.poller = poller;
        this.pollRunRepository = pollRunRepository;
        this.snapshotRepository = snapshotRepository;
        this.anomalyRepository = anomalyRepository;
    }

    public SummaryResponse summary() {
        var latestSuccess = pollRunRepository.findTopByStatusOrderByCompletedAtDesc(PollStatus.SUCCESS).orElse(null);
        var latestAttempt = pollRunRepository.findTopByOrderByStartedAtDesc().orElse(null);
        var snapshots = latestSuccess == null ? List.<RankingSnapshot>of()
                : snapshotRepository.findByPollRunId(latestSuccess.getId());
        List<AnomalyEvent> anomalies = latestSuccess == null ? List.of()
                : latestSuccess.getTopicCount() == 0 ? List.of()
                : snapshots.stream().map(RankingSnapshot::getTopic).distinct()
                    .flatMap(topic -> anomalyRepository.findByPollRunIdAndTopic(latestSuccess.getId(), topic).stream())
                    .toList();

        var anomalyByTopic = anomalies.stream().collect(Collectors.groupingBy(
                AnomalyEvent::getTopic, Collectors.counting()));
        var topics = snapshots.stream().collect(Collectors.groupingBy(RankingSnapshot::getTopic)).entrySet().stream()
                .map(entry -> new TopicSummary(
                        entry.getKey(),
                        entry.getValue().stream().filter(RankingSnapshot::isPresent).count(),
                        entry.getValue().stream().filter(row -> row.getChangeState() == ChangeState.DROPPED).count(),
                        entry.getValue().stream().filter(row -> row.getChangeState() != ChangeState.UNCHANGED
                                && row.getChangeState() != ChangeState.ABSENT).count(),
                        anomalyByTopic.getOrDefault(entry.getKey(), 0L)))
                .sorted(Comparator.comparing(TopicSummary::topic))
                .toList();

        var teams = snapshots.stream().filter(RankingSnapshot::isPresent)
                .map(row -> row.getTeamName().toLowerCase()).collect(Collectors.toSet());
        return new SummaryResponse(
                Instant.now(), properties.contestId(), latestSuccess == null ? null : latestSuccess.getContestName(),
                poller.isRunning(), properties.pollIntervalMs() / 1000,
                snapshots.stream().filter(RankingSnapshot::isPresent).count(), teams.size(),
                anomalyRepository.countByDetectedAtAfter(Instant.now().minus(24, ChronoUnit.HOURS)),
                toPollRun(latestSuccess), toPollRun(latestAttempt), topics, DISCLAIMER);
    }

    public PagedResponse<RankingRow> rankings(String topic, String teamName, int page, int size) {
        var run = latestSuccessfulRun();
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        var result = snapshotRepository.findByPollRunIdAndTopicAndPresentTrueAndTeamNameContainingIgnoreCase(
                run.getId(), required(topic, "topic"), teamName == null ? "" : teamName.trim(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "ranking")));
        var signals = signalsBySnapshot(result.getContent().stream().map(RankingSnapshot::getId).toList());
        var items = result.getContent().stream().map(row -> toRankingRow(row, signals.get(row.getId()))).toList();
        return new PagedResponse<>(items, result.getNumber(), result.getSize(), result.getTotalPages(),
                result.getTotalElements(), run.getId(), run.getCompletedAt());
    }

    public TeamSearchResponse searchTeams(String query) {
        var teams = snapshotRepository.searchTeamNames(properties.contestId(),
                query == null ? "" : query.trim(), PageRequest.of(0, 20));
        return new TeamSearchResponse(teams);
    }

    public HistoryResponse history(String topic, String teamName, int hours) {
        var safeHours = Math.max(1, Math.min(hours, properties.retentionDays() * 24));
        var to = Instant.now();
        var from = to.minus(safeHours, ChronoUnit.HOURS);
        var all = snapshotRepository
                .findByContestIdAndTopicAndTeamNameIgnoreCaseAndObservedAtBetweenOrderByObservedAtAsc(
                        properties.contestId(), required(topic, "topic"), required(teamName, "teamName"), from, to);
        var allSignals = signalsBySnapshot(all.stream().map(RankingSnapshot::getId).toList());
        var sampled = downsample(all, allSignals.keySet(), properties.historyMaxPoints());
        var points = sampled.stream().map(row -> new HistoryPoint(
                row.getId(), row.getObservedAt(), row.isPresent(), row.getRanking(), row.getTakeTime(),
                row.getCommitTimes(), row.getBestTakeTime(), row.getRankChange(), row.getTakeTimeChangePct(),
                row.getCommitDelta(), row.getChangeState(), allSignals.getOrDefault(row.getId(), List.of())))
                .toList();
        return new HistoryResponse(teamName, topic, from, to, all.size(), points);
    }

    public SimplePage<AnomalyDto> anomalies(String topic, String teamName, String type,
                                            int hours, int page, int size) {
        var safeHours = Math.max(1, Math.min(hours, properties.retentionDays() * 24));
        AnomalyType parsedType = null;
        if (type != null && !type.isBlank()) {
            try {
                parsedType = AnomalyType.valueOf(type);
            } catch (IllegalArgumentException error) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "未知异常类型: " + type);
            }
        }
        final var finalType = parsedType;
        Specification<AnomalyEvent> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("detectedAt"),
                    Instant.now().minus(safeHours, ChronoUnit.HOURS)));
            if (topic != null && !topic.isBlank()) {
                predicates.add(cb.equal(root.get("topic"), topic));
            }
            if (teamName != null && !teamName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("teamName")),
                        "%" + teamName.trim().toLowerCase() + "%"));
            }
            if (finalType != null) {
                predicates.add(cb.equal(root.get("type"), finalType));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        int safeSize = Math.max(1, Math.min(size, 100));
        var result = anomalyRepository.findAll(spec,
                PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "detectedAt")));
        return new SimplePage<>(result.getContent().stream().map(DashboardService::toAnomaly).toList(),
                result.getNumber(), result.getSize(), result.getTotalPages(), result.getTotalElements());
    }

    public List<PollRunDto> pollRuns() {
        return pollRunRepository.findTop20ByOrderByStartedAtDesc().stream()
                .map(DashboardService::toPollRun)
                .toList();
    }

    private PollRun latestSuccessfulRun() {
        return pollRunRepository.findTopByStatusOrderByCompletedAtDesc(PollStatus.SUCCESS)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "尚无成功采集数据，请稍后刷新"));
    }

    private Map<Long, List<SignalDto>> signalsBySnapshot(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return anomalyRepository.findBySnapshotIdIn(ids).stream().collect(Collectors.groupingBy(
                AnomalyEvent::getSnapshotId,
                Collectors.mapping(event -> new SignalDto(event.getType(), event.getSeverity(), event.getTitle()),
                        Collectors.toList())));
    }

    private static RankingRow toRankingRow(RankingSnapshot row, List<SignalDto> signals) {
        return new RankingRow(row.getId(), row.getTopic(), row.getTeamName(), row.getUnit(), row.getRanking(),
                row.getTakeTime(), row.getCommitTimes(), row.getLastCommitAt(), row.isFastest(),
                row.getBestTakeTime(), row.getRankChange(), row.getTakeTimeChangePct(), row.getCommitDelta(),
                row.getChangeState(), row.getObservedAt(), signals == null ? List.of() : signals);
    }

    private static AnomalyDto toAnomaly(AnomalyEvent event) {
        return new AnomalyDto(event.getId(), event.getSnapshotId(), event.getPollRunId(), event.getTopic(),
                event.getTeamName(), event.getType(), event.getSeverity(), event.getTitle(), event.getDescription(),
                event.getPreviousTakeTime(), event.getCurrentTakeTime(), event.getBaselineTakeTime(),
                event.getDetectedAt());
    }

    private static PollRunDto toPollRun(PollRun run) {
        return run == null ? null : new PollRunDto(run.getId(), run.getStatus(), run.getStartedAt(),
                run.getCompletedAt(), run.getTopicCount(), run.getSnapshotCount(), run.getChangedCount(),
                run.getAnomalyCount(), run.getErrorMessage());
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " 不能为空");
        }
        return value.trim();
    }

    private static List<RankingSnapshot> downsample(List<RankingSnapshot> source, Set<Long> pinnedIds, int maxPoints) {
        if (source.size() <= maxPoints || maxPoints < 3) {
            return source;
        }
        var selected = new LinkedHashSet<Integer>();
        selected.add(0);
        selected.add(source.size() - 1);
        for (int i = 0; i < source.size(); i++) {
            if (pinnedIds.contains(source.get(i).getId())) {
                selected.add(i);
            }
        }
        int slots = Math.max(0, maxPoints - selected.size());
        for (int i = 1; i <= slots; i++) {
            int index = (int) Math.round((double) i * (source.size() - 1) / (slots + 1));
            selected.add(index);
        }
        return selected.stream().sorted().map(source::get).toList();
    }
}
