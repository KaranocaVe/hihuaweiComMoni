package com.ascend.monitor.repository;

import java.math.BigDecimal;
import java.time.Instant;

public interface ReconstructedHistoryRow {

    Long getSnapshotId();
    Long getSignalSnapshotId();
    Instant getObservedAt();
    Boolean getPresent();
    Integer getRanking();
    BigDecimal getTakeTime();
    Integer getCommitTimes();
    BigDecimal getBestTakeTime();
    Integer getRankChange();
    BigDecimal getTakeTimeChangePct();
    Integer getCommitDelta();
    String getChangeState();
}
