package com.ascend.monitor.polling;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.ascend.monitor.client.ContestDetails;
import com.ascend.monitor.client.RankEntry;
import com.ascend.monitor.detection.AnomalyDraft;
import com.ascend.monitor.detection.DetectionEngine;
import com.ascend.monitor.domain.AnomalyEvent;
import com.ascend.monitor.domain.ChangeState;
import com.ascend.monitor.domain.PollRun;
import com.ascend.monitor.domain.PollStatus;
import com.ascend.monitor.domain.RankingSnapshot;
import com.ascend.monitor.repository.AnomalyEventRepository;
import com.ascend.monitor.repository.PollRunRepository;
import com.ascend.monitor.repository.RankingSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollPersistenceService {

    private static final ZoneId SOURCE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter SOURCE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private final PollRunRepository pollRunRepository;
    private final RankingSnapshotRepository snapshotRepository;
    private final AnomalyEventRepository anomalyRepository;
    private final DetectionEngine detectionEngine;

    public PollPersistenceService(PollRunRepository pollRunRepository,
                                  RankingSnapshotRepository snapshotRepository,
                                  AnomalyEventRepository anomalyRepository,
                                  DetectionEngine detectionEngine) {
        this.pollRunRepository = pollRunRepository;
        this.snapshotRepository = snapshotRepository;
        this.anomalyRepository = anomalyRepository;
        this.detectionEngine = detectionEngine;
    }

    @Transactional
    public PollOutcome persist(UUID runId, ContestDetails contest,
                               Map<String, List<RankEntry>> topicEntries, Instant observedAt) {
        PollRun run = pollRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("采集批次不存在: " + runId));
        var previousRun = pollRunRepository.findTopByStatusOrderByCompletedAtDesc(PollStatus.SUCCESS).orElse(null);

        var snapshots = new ArrayList<RankingSnapshot>();
        var pendingAnomalies = new IdentityHashMap<RankingSnapshot, List<AnomalyDraft>>();
        int changedCount = 0;

        for (var topicEntry : topicEntries.entrySet()) {
            var topic = topicEntry.getKey();
            var currentByTeam = indexCurrent(topicEntry.getValue());
            var previousByTeam = previousRun == null
                    ? Map.<String, RankingSnapshot>of()
                    : indexPrevious(snapshotRepository.findByPollRunIdAndTopic(previousRun.getId(), topic));

            var teamKeys = new LinkedHashSet<String>();
            teamKeys.addAll(currentByTeam.keySet());
            teamKeys.addAll(previousByTeam.keySet());

            for (var teamKey : teamKeys) {
                var currentEntry = currentByTeam.get(teamKey);
                var previous = previousByTeam.get(teamKey);
                var snapshot = buildSnapshot(runId, contest.gameId(), topic, currentEntry, previous, observedAt);
                snapshots.add(snapshot);
                if (snapshot.getChangeState() != ChangeState.UNCHANGED
                        && snapshot.getChangeState() != ChangeState.ABSENT) {
                    changedCount++;
                }
                pendingAnomalies.put(snapshot, detectionEngine.evaluate(previous, snapshot));
            }
        }

        snapshotRepository.saveAll(snapshots);
        snapshotRepository.flush();

        var anomalyEvents = new ArrayList<AnomalyEvent>();
        for (var snapshot : snapshots) {
            for (var draft : pendingAnomalies.getOrDefault(snapshot, List.of())) {
                anomalyEvents.add(toEntity(snapshot, draft));
            }
        }
        anomalyRepository.saveAll(anomalyEvents);
        run.complete(contest.gameName(), topicEntries.size(), snapshots.size(), changedCount,
                anomalyEvents.size(), observedAt);
        pollRunRepository.save(run);
        return new PollOutcome(snapshots.size(), changedCount, anomalyEvents.size());
    }

    @Transactional
    public void markFailed(UUID runId, Throwable error, Instant now) {
        pollRunRepository.findById(runId).ifPresent(run -> {
            run.fail(error, now);
            pollRunRepository.save(run);
        });
    }

    private static RankingSnapshot buildSnapshot(UUID runId, String contestId, String topic,
                                                  RankEntry current, RankingSnapshot previous,
                                                  Instant observedAt) {
        if (current == null) {
            return RankingSnapshot.create(
                    runId, contestId, topic,
                    previous.getTeamName(), previous.getUnit(), false,
                    null, null, previous.getCommitTimes(), previous.getLastCommitAt(), false,
                    previous.getBestTakeTime(), null, null, 0,
                    previous.isPresent() ? ChangeState.DROPPED : ChangeState.ABSENT,
                    observedAt);
        }

        var takeTime = positive(current.takeTime());
        var priorBest = previous == null ? null : positive(previous.getBestTakeTime());
        var previousTakeTime = previous == null ? null : positive(previous.getTakeTime());
        var bestTakeTime = takeTime == null ? priorBest
                : priorBest == null ? takeTime : takeTime.min(priorBest);
        var rankChange = previous == null || previous.getRanking() == null || current.ranking() == null
                ? null : previous.getRanking() - current.ranking();
        var commitDelta = previous == null || previous.getCommitTimes() == null || current.commitTimes() == null
                ? null : current.commitTimes() - previous.getCommitTimes();
        var takeTimeChange = previousTakeTime == null || takeTime == null
                ? null
                : takeTime.subtract(previousTakeTime)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previousTakeTime, 4, RoundingMode.HALF_UP);

        var state = determineState(current, previous, takeTime);
        return RankingSnapshot.create(
                runId, contestId, topic,
                current.teamName().trim(), blankToNull(current.unit()), true,
                current.ranking(), takeTime, current.commitTimes(), parseSourceTime(current.lastCommit()),
                Boolean.TRUE.equals(current.fastest()), bestTakeTime,
                rankChange, takeTimeChange, commitDelta, state, observedAt);
    }

    private static ChangeState determineState(RankEntry current, RankingSnapshot previous, BigDecimal takeTime) {
        if (previous == null) {
            return ChangeState.NEW;
        }
        if (!previous.isPresent()) {
            return ChangeState.RETURNED;
        }
        boolean unchanged = equal(previous.getRanking(), current.ranking())
                && equal(previous.getTakeTime(), takeTime)
                && equal(previous.getCommitTimes(), current.commitTimes())
                && equal(previous.getLastCommitAt(), parseSourceTime(current.lastCommit()));
        return unchanged ? ChangeState.UNCHANGED : ChangeState.CHANGED;
    }

    private static Map<String, RankEntry> indexCurrent(List<RankEntry> entries) {
        var result = new HashMap<String, RankEntry>();
        for (var entry : entries) {
            if (entry.teamName() != null && !entry.teamName().isBlank()) {
                result.put(normalize(entry.teamName()), entry);
            }
        }
        return result;
    }

    private static Map<String, RankingSnapshot> indexPrevious(List<RankingSnapshot> entries) {
        var result = new HashMap<String, RankingSnapshot>();
        for (var entry : entries) {
            result.put(normalize(entry.getTeamName()), entry);
        }
        return result;
    }

    private static AnomalyEvent toEntity(RankingSnapshot snapshot, AnomalyDraft draft) {
        return AnomalyEvent.create(snapshot, draft.type(), draft.severity(), draft.title(), draft.description(),
                draft.previousTakeTime(), draft.currentTakeTime(), draft.baselineTakeTime());
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal positive(BigDecimal value) {
        return value != null && value.signum() > 0 ? value : null;
    }

    private static Instant parseSourceTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, SOURCE_TIME).atZone(SOURCE_ZONE).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static boolean equal(Object left, Object right) {
        if (left instanceof BigDecimal a && right instanceof BigDecimal b) {
            return a.compareTo(b) == 0;
        }
        return java.util.Objects.equals(left, right);
    }

    public record PollOutcome(int snapshots, int changed, int anomalies) {
    }
}
