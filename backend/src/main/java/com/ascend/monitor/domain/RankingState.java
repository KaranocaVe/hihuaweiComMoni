package com.ascend.monitor.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Absolute leaderboard state shared by persisted change events and the compact current-state table.
 */
public interface RankingState {

    String getContestId();
    String getTopic();
    String getTeamName();
    String getUnit();
    boolean isPresent();
    Integer getRanking();
    BigDecimal getTakeTime();
    Integer getCommitTimes();
    Instant getLastCommitAt();
    boolean isFastest();
    BigDecimal getBestTakeTime();
}
