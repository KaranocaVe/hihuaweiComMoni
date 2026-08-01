package com.ascend.monitor.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ascend.monitor.domain.AnomalyType;
import com.ascend.monitor.domain.ChangeState;
import com.ascend.monitor.domain.PollStatus;

public final class ApiModels {

    private ApiModels() {
    }

    public record PollRunDto(
            UUID id,
            PollStatus status,
            Instant startedAt,
            Instant completedAt,
            int topicCount,
            int snapshotCount,
            int changedCount,
            int anomalyCount,
            String errorMessage
    ) {
    }

    public record TopicSummary(
            String topic,
            long presentTeams,
            long droppedTeams,
            long changedTeams,
            long anomalyCount
    ) {
    }

    public record SummaryResponse(
            Instant serverTime,
            String contestId,
            String contestName,
            boolean pollerRunning,
            long pollIntervalSeconds,
            long currentRows,
            long currentTeams,
            long anomaliesLast24Hours,
            PollRunDto latestSuccessfulRun,
            PollRunDto latestAttempt,
            List<TopicSummary> topics,
            String disclaimer
    ) {
    }

    public record SignalDto(AnomalyType type, int severity, String title) {
    }

    public record RankingRow(
            Long snapshotId,
            String topic,
            String teamName,
            String unit,
            Integer ranking,
            BigDecimal takeTime,
            Integer commitTimes,
            Instant lastCommitAt,
            boolean fastest,
            BigDecimal bestTakeTime,
            Integer rankChange,
            BigDecimal takeTimeChangePct,
            Integer commitDelta,
            ChangeState changeState,
            Instant observedAt,
            List<SignalDto> signals
    ) {
    }

    public record PagedResponse<T>(
            List<T> items,
            int page,
            int size,
            int totalPages,
            long totalElements,
            UUID pollRunId,
            Instant observedAt
    ) {
    }

    public record SimplePage<T>(
            List<T> items,
            int page,
            int size,
            int totalPages,
            long totalElements
    ) {
    }

    public record HistoryPoint(
            Long snapshotId,
            Instant observedAt,
            boolean present,
            Integer ranking,
            BigDecimal takeTime,
            Integer commitTimes,
            BigDecimal bestTakeTime,
            Integer rankChange,
            BigDecimal takeTimeChangePct,
            Integer commitDelta,
            ChangeState changeState,
            List<SignalDto> signals
    ) {
    }

    public record HistoryResponse(
            String teamName,
            String topic,
            Instant from,
            Instant to,
            int originalPointCount,
            List<HistoryPoint> points
    ) {
    }

    public record AnomalyDto(
            Long id,
            Long snapshotId,
            UUID pollRunId,
            String topic,
            String teamName,
            AnomalyType type,
            int severity,
            String title,
            String description,
            BigDecimal previousTakeTime,
            BigDecimal currentTakeTime,
            BigDecimal baselineTakeTime,
            Instant detectedAt
    ) {
    }

    public record TeamSearchResponse(List<String> teams) {
    }
}
