package com.ascend.monitor.detection;

import java.math.BigDecimal;
import java.time.Instant;

public record TeamRiskScore(
        String teamName,
        int score,
        String level,
        long signalCount,
        int topicCount,
        long hidingCount,
        long regressionCount,
        long reboundCount,
        long droppedCount,
        long returnedCount,
        BigDecimal averageSeverity,
        Instant lastDetectedAt,
        long submissionRounds,
        long commitCount,
        long scorelessSubmissions,
        long scorelessReturns,
        long hidingEntries,
        long recoveries,
        double submissionsPerHour,
        int activeTopicCount,
        long longHoldRecoveries,
        long veryLongRecoveries,
        Double medianRecoveryHours
) {
}
