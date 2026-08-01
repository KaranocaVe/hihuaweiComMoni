package com.ascend.monitor.detection;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ascend.monitor.config.MonitorProperties;
import com.ascend.monitor.domain.AnomalyType;
import com.ascend.monitor.domain.ChangeState;
import com.ascend.monitor.domain.RankingSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DetectionEngineTest {

    private DetectionEngine engine;

    @BeforeEach
    void setUp() {
        var properties = new MonitorProperties(
                "contest", "https://example.com/api", "https://example.com/rank",
                120_000, 0, 50, 1200, 90,
                new MonitorProperties.Detection(15, 25, 15, 5, 8));
        engine = new DetectionEngine(properties);
    }

    @Test
    void flagsRegressionPossibleHidingAndSubmissionBurst() {
        var previous = snapshot(true, "100", "80", 12, null, null, ChangeState.CHANGED);
        var current = snapshot(true, "140", "80", 22, 10, "40", ChangeState.CHANGED);

        var types = engine.evaluate(previous, current).stream().map(AnomalyDraft::type).toList();

        assertThat(types).contains(
                AnomalyType.SCORE_REGRESSION,
                AnomalyType.POSSIBLE_HIDING,
                AnomalyType.SUBMISSION_BURST);
    }

    @Test
    void flagsReboundToHistoricalBest() {
        var previous = snapshot(true, "140", "100", 10, null, null, ChangeState.CHANGED);
        var current = snapshot(true, "103", "100", 12, 2, "-26.4286", ChangeState.CHANGED);

        assertThat(engine.evaluate(previous, current).stream().map(AnomalyDraft::type))
                .contains(AnomalyType.SCORE_REBOUND);
    }

    @Test
    void flagsDropAndStopsOtherNumericChecks() {
        var previous = snapshot(true, "100", "95", 10, null, null, ChangeState.CHANGED);
        var current = snapshot(false, null, "95", 10, 0, null, ChangeState.DROPPED);

        assertThat(engine.evaluate(previous, current).stream().map(AnomalyDraft::type).toList())
                .containsExactly(AnomalyType.DROPPED_FROM_BOARD);
    }

    @Test
    void doesNotAccuseOnNormalSmallMovement() {
        var previous = snapshot(true, "100", "95", 10, null, null, ChangeState.CHANGED);
        var current = snapshot(true, "104", "95", 11, 1, "4", ChangeState.CHANGED);

        assertThat(engine.evaluate(previous, current)).isEmpty();
    }

    private static RankingSnapshot snapshot(boolean present, String takeTime, String bestTakeTime,
                                            Integer commitTimes, Integer commitDelta,
                                            String changePct, ChangeState state) {
        return RankingSnapshot.create(
                UUID.randomUUID(), "contest", "Concat", "team", null, present,
                present ? 1 : null, decimal(takeTime), commitTimes, Instant.now(), false,
                decimal(bestTakeTime), 0, decimal(changePct), commitDelta, state, Instant.now());
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
