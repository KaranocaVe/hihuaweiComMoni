package com.ascend.monitor.detection;

import java.util.Comparator;
import java.util.List;

import com.ascend.monitor.repository.TeamAnomalySummary;
import org.springframework.stereotype.Component;

/**
 * Converts independent signals into a bounded team-level investigation priority.
 * This is not a claim of rule violation.
 */
@Component
public class TeamRiskScorer {

    public List<TeamRiskScore> rank(List<TeamAnomalySummary> summaries) {
        return rank(summaries, 24);
    }

    public List<TeamRiskScore> rank(List<TeamAnomalySummary> summaries, int observationHours) {
        return summaries.stream()
                .map(summary -> score(summary, observationHours))
                .sorted(Comparator.comparingInt(TeamRiskScore::score).reversed()
                        .thenComparing(TeamRiskScore::signalCount, Comparator.reverseOrder())
                        .thenComparing(TeamRiskScore::teamName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    TeamRiskScore score(TeamAnomalySummary summary) {
        return score(summary, 24);
    }

    private TeamRiskScore score(TeamAnomalySummary summary, int observationHours) {
        // The core signal is a bad visible score held for a while and then replaced by a
        // near-best score. Fast oscillation is treated as exploration, not concealment.
        int longHoldEvidence = Math.min(60, (int) summary.getLongHoldRecoveries() * 25);
        int longHoldRateBonus = summary.getRecoveries() == 0 ? 0
                : Math.min(20, (int) Math.round(20.0 * summary.getLongHoldRecoveries()
                        / summary.getRecoveries()));
        int entryEvidence = Math.min(15, (int) summary.getHidingEntries() * 3);
        int scorelessEvidence = Math.min(10, (int) (summary.getScorelessSubmissions()
                + summary.getScorelessReturns()) * 2);
        int episodeEvidence = longHoldEvidence + longHoldRateBonus + entryEvidence + scorelessEvidence;
        double submissionsPerHour = summary.getSubmissionRounds() / (double) Math.max(1, observationHours);
        double cadenceFactor = submissionsPerHour <= 1 ? 1.0
                : submissionsPerHour <= 2 ? 0.8
                : submissionsPerHour <= 4 ? 0.4
                : 0.15;
        double evidenceQuality = Math.min(1.0, summary.getHidingEntries() / 3.0);
        int breadth = Math.min(10, summary.getActiveTopicCount() * 3);
        int score = Math.min(100, (int) Math.round(episodeEvidence * evidenceQuality * cadenceFactor + breadth));
        String level = score >= 70 ? "HIGH" : score >= 35 ? "WATCH" : "LOW";
        return new TeamRiskScore(summary.getTeamName(), score, level,
                summary.getSignalCount(), summary.getTopicCount(), summary.getHidingCount(),
                summary.getRegressionCount(), summary.getReboundCount(), summary.getDroppedCount(),
                summary.getReturnedCount(), summary.getAverageSeverity(), summary.getLastDetectedAt(),
                summary.getSubmissionRounds(), summary.getCommitCount(), summary.getScorelessSubmissions(),
                summary.getScorelessReturns(), summary.getHidingEntries(), summary.getRecoveries(),
                submissionsPerHour, summary.getActiveTopicCount(), summary.getLongHoldRecoveries(),
                summary.getVeryLongRecoveries(), summary.getMedianRecoveryHours());
    }
}
