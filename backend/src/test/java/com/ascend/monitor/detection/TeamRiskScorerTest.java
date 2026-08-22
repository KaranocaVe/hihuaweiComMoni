package com.ascend.monitor.detection;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ascend.monitor.repository.TeamAnomalySummary;
import org.junit.jupiter.api.Test;

class TeamRiskScorerTest {

    private final TeamRiskScorer scorer = new TeamRiskScorer();

    @Test
    void prioritizesCrossTopicAlternatingEvidence() {
        var noisySingleTopic = summary("6666", 20, 5, 20, 0, 0, 0, 0, 90,
                120, 120, 0, 0, 12, 4, 0, 0, null);
        var alternatingAcrossTopics = summary("天南星", 8, 3, 3, 2, 2, 0, 0, 88,
                24, 24, 4, 4, 3, 2, 2, 2, 4.2);

        var ranked = scorer.rank(List.of(noisySingleTopic, alternatingAcrossTopics));

        assertThat(ranked).extracting(TeamRiskScore::teamName).containsExactly("天南星", "6666");
        assertThat(ranked.getFirst().level()).isEqualTo("HIGH");
    }

    private static TeamAnomalySummary summary(String team, long signals, int topics, long hiding,
                                               long regression, long rebound, long dropped,
                                               long returned, double severity, long submissionRounds,
                                               long commitCount, long scorelessSubmissions,
                                               long scorelessReturns, long hidingEntries, long recoveries,
                                               long longHolds, long veryLong, Double medianRecovery) {
        return new TeamAnomalySummary() {
            public String getTeamKey() { return team.toLowerCase(); }
            public String getTeamName() { return team; }
            public long getSignalCount() { return signals; }
            public int getTopicCount() { return topics; }
            public long getHidingCount() { return hiding; }
            public long getRegressionCount() { return regression; }
            public long getReboundCount() { return rebound; }
            public long getDroppedCount() { return dropped; }
            public long getReturnedCount() { return returned; }
            public BigDecimal getAverageSeverity() { return BigDecimal.valueOf(severity); }
            public Instant getLastDetectedAt() { return Instant.EPOCH; }
            public long getSubmissionRounds() { return submissionRounds; }
            public int getActiveTopicCount() { return topics; }
            public long getCommitCount() { return commitCount; }
            public long getScorelessSubmissions() { return scorelessSubmissions; }
            public long getScorelessReturns() { return scorelessReturns; }
            public long getHidingEntries() { return hidingEntries; }
            public long getRecoveries() { return recoveries; }
            public long getLongHoldRecoveries() { return longHolds; }
            public long getVeryLongRecoveries() { return veryLong; }
            public Double getMedianRecoveryHours() { return medianRecovery; }
        };
    }
}
