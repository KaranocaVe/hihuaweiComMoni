package com.ascend.monitor.repository;

import java.math.BigDecimal;
import java.time.Instant;

/** Aggregated anomaly evidence and submission behavior for one team in a bounded window. */
public interface TeamAnomalySummary {

    String getTeamKey();

    String getTeamName();

    long getSignalCount();

    int getTopicCount();

    long getHidingCount();

    long getRegressionCount();

    long getReboundCount();

    long getDroppedCount();

    long getReturnedCount();

    BigDecimal getAverageSeverity();

    Instant getLastDetectedAt();

    long getSubmissionRounds();

    int getActiveTopicCount();

    long getCommitCount();

    long getScorelessSubmissions();

    long getScorelessReturns();

    long getHidingEntries();

    long getRecoveries();

    long getLongHoldRecoveries();

    long getVeryLongRecoveries();

    Double getMedianRecoveryHours();
}
