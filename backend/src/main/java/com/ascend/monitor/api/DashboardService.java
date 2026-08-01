package com.ascend.monitor.api;

import static com.ascend.monitor.api.ApiModels.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ascend.monitor.config.MonitorProperties;
import com.ascend.monitor.domain.AnomalyEvent;
import com.ascend.monitor.domain.AnomalyType;
import com.ascend.monitor.domain.ChangeState;
import com.ascend.monitor.domain.CurrentRanking;
import com.ascend.monitor.domain.PollRun;
import com.ascend.monitor.domain.PollStatus;
import com.ascend.monitor.domain.RankingSnapshot;
import com.ascend.monitor.polling.LeaderboardPoller;
import com.ascend.monitor.repository.AnomalyEventRepository;
import com.ascend.monitor.repository.CurrentRankingRepository;
import com.ascend.monitor.repository.PollRunRepository;
import com.ascend.monitor.repository.RankingSnapshotRepository;
import jakarta.persistence.criteria.Predicate;
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
    private final CurrentRankingRepository currentRepository;
    private final AnomalyEventRepository anomalyRepository;

    public DashboardService(MonitorProperties properties, LeaderboardPoller poller,
                            PollRunRepository pollRunRepository,
                            RankingSnapshotRepository snapshotRepository,
                            CurrentRankingRepository currentRepository,
                            AnomalyEventRepository anomalyRepository) {
        this.properties = properties;
        this.poller = poller;
        this.pollRunRepository = pollRunRepository;
        this.snapshotRepository = snapshotRepository;
        this.currentRepository = currentRepository;
        this.anomalyRepository = anomalyRepository;
    }

    public SummaryResponse summary() {
        var latestSuccess = pollRunRepository.findTopByStatusOrderByCompletedAtDesc(PollStatus.SUCCESS).orElse(null);
        var latestAttempt = pollRunRepository.findTopByOrderByStartedAtDesc().orElse(null);
        var currentRows = currentRepository.findByContestId(properties.contestId());
        List<RankingSnapshot> latestEvents = latestSuccess == null ? List.of()
                : snapshotRepository.findByPollRunId(latestSuccess.getId());
        List<AnomalyEvent> anomalies = latestSuccess == null ? List.of()
                : anomalyRepository.findByPollRunId(latestSuccess.getId());

        var anomalyByTopic = anomalies.stream().collect(Collectors.groupingBy(
                AnomalyEvent::getTopic, Collectors.counting()));
        var eventsByTopic = latestEvents.stream().collect(Collectors.groupingBy(row -> row.getTopic()));
        var topics = currentRows.stream().collect(Collectors.groupingBy(CurrentRanking::getTopic)).entrySet().stream()
                .map(entry -> new TopicSummary(
                        entry.getKey(),
                        entry.getValue().stream().filter(CurrentRanking::isPresent).count(),
                        eventsByTopic.getOrDefault(entry.getKey(), List.of()).stream()
                                .filter(row -> row.getChangeState() == ChangeState.DROPPED).count(),
                        eventsByTopic.getOrDefault(entry.getKey(), List.of()).stream()
                                .filter(row -> row.getChangeState() != ChangeState.UNCHANGED
                                && row.getChangeState() != ChangeState.ABSENT).count(),
                        anomalyByTopic.getOrDefault(entry.getKey(), 0L)))
                .sorted(Comparator.comparing(TopicSummary::topic))
                .toList();

        var teams = currentRows.stream().filter(CurrentRanking::isPresent)
                .map(row -> row.getTeamName().toLowerCase()).collect(Collectors.toSet());
        return new SummaryResponse(
                Instant.now(), properties.contestId(), latestSuccess == null ? null : latestSuccess.getContestName(),
                poller.isRunning(), properties.pollIntervalMs() / 1000,
                currentRows.stream().filter(CurrentRanking::isPresent).count(), teams.size(),
                anomalyRepository.countByDetectedAtAfter(Instant.now().minus(24, ChronoUnit.HOURS)),
                toPollRun(latestSuccess), toPollRun(latestAttempt), topics, DISCLAIMER);
    }

    public PagedResponse<RankingRow> rankings(String topic, String teamName, int page, int size) {
        var run = latestSuccessfulRun();
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        var result = currentRepository.findByContestIdAndTopicAndPresentTrueAndTeamNameContainingIgnoreCase(
                properties.contestId(), required(topic, "topic"), teamName == null ? "" : teamName.trim(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "ranking")));
        var eventIds = result.getContent().stream()
                .filter(row -> run.getId().equals(row.getPollRunId()))
                .map(CurrentRanking::getSnapshotId)
                .toList();
        var signals = signalsBySnapshot(eventIds);
        var items = result.getContent().stream()
                .map(row -> toRankingRow(row, run, signals.get(row.getSnapshotId())))
                .toList();
        return new PagedResponse<>(items, result.getNumber(), result.getSize(), result.getTotalPages(),
                result.getTotalElements(), run.getId(), run.getCompletedAt());
    }

    public TeamSearchResponse searchTeams(String query) {
        var teams = currentRepository.searchTeamNames(properties.contestId(),
                query == null ? "" : query.trim(), PageRequest.of(0, 20));
        return new TeamSearchResponse(teams);
    }

    public HistoryResponse history(String topic, String teamName, int hours) {
        var safeHours = Math.max(1, Math.min(hours, properties.retentionDays() * 24));
        var to = Instant.now();
        var from = to.minus(safeHours, ChronoUnit.HOURS);
        var reconstructed = snapshotRepository.reconstructHistory(
                properties.contestId(), required(topic, "topic"), required(teamName, "teamName"), from, to);
        var signalIds = reconstructed.stream().map(row -> row.getSignalSnapshotId())
                .filter(java.util.Objects::nonNull).distinct().toList();
        var allSignals = signalsBySnapshot(signalIds);
        var all = reconstructed.stream().map(row -> new HistorySample(
                row.getSnapshotId(), row.getSignalSnapshotId(), row.getObservedAt(),
                Boolean.TRUE.equals(row.getPresent()), row.getRanking(), row.getTakeTime(),
                row.getCommitTimes(), row.getBestTakeTime(), row.getRankChange(),
                row.getTakeTimeChangePct(), row.getCommitDelta(), ChangeState.valueOf(row.getChangeState())))
                .toList();
        var sampled = downsample(all, properties.historyMaxPoints());
        var points = sampled.stream().map(row -> new HistoryPoint(
                row.snapshotId(), row.observedAt(), row.present(), row.ranking(), row.takeTime(),
                row.commitTimes(), row.bestTakeTime(), row.rankChange(), row.takeTimeChangePct(),
                row.commitDelta(), row.changeState(), row.signalSnapshotId() == null
                        ? List.of() : allSignals.getOrDefault(row.signalSnapshotId(), List.of())))
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

    private static RankingRow toRankingRow(CurrentRanking row, PollRun run, List<SignalDto> signals) {
        boolean changedInLatestRun = run.getId().equals(row.getPollRunId());
        var state = changedInLatestRun ? row.getChangeState() : ChangeState.UNCHANGED;
        var rankChange = changedInLatestRun ? row.getRankChange() : row.getRanking() == null ? null : 0;
        var takeTimeChange = changedInLatestRun ? row.getTakeTimeChangePct()
                : row.getTakeTime() == null ? null : BigDecimal.ZERO;
        var commitDelta = changedInLatestRun ? row.getCommitDelta()
                : row.getCommitTimes() == null ? null : 0;
        return new RankingRow(row.getSnapshotId(), row.getTopic(), row.getTeamName(), row.getUnit(), row.getRanking(),
                row.getTakeTime(), row.getCommitTimes(), row.getLastCommitAt(), row.isFastest(),
                row.getBestTakeTime(), rankChange, takeTimeChange, commitDelta,
                state, run.getCompletedAt(), signals == null ? List.of() : signals);
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

    private static List<HistorySample> downsample(List<HistorySample> source, int maxPoints) {
        if (source.size() <= maxPoints || maxPoints < 3) {
            return source;
        }
        var selected = new LinkedHashSet<Integer>();
        selected.add(0);
        selected.add(source.size() - 1);
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i).signalSnapshotId() != null) {
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

    private record HistorySample(
            Long snapshotId,
            Long signalSnapshotId,
            Instant observedAt,
            boolean present,
            Integer ranking,
            BigDecimal takeTime,
            Integer commitTimes,
            BigDecimal bestTakeTime,
            Integer rankChange,
            BigDecimal takeTimeChangePct,
            Integer commitDelta,
            ChangeState changeState
    ) {
    }
}
