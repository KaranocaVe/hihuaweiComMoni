package com.ascend.monitor.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ranking_snapshot")
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID pollRunId;

    @Column(nullable = false, length = 64)
    private String contestId;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(nullable = false)
    private String teamName;

    private String unit;
    private boolean present;
    private Integer ranking;

    @Column(precision = 20, scale = 6)
    private BigDecimal takeTime;

    private Integer commitTimes;
    private Instant lastCommitAt;
    private boolean fastest;

    @Column(precision = 20, scale = 6)
    private BigDecimal bestTakeTime;

    private Integer rankChange;

    @Column(precision = 12, scale = 4)
    private BigDecimal takeTimeChangePct;

    private Integer commitDelta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ChangeState changeState;

    @Column(nullable = false)
    private Instant observedAt;

    protected RankingSnapshot() {
    }

    public static RankingSnapshot create(UUID pollRunId, String contestId, String topic,
                                         String teamName, String unit, boolean present,
                                         Integer ranking, BigDecimal takeTime, Integer commitTimes,
                                         Instant lastCommitAt, boolean fastest, BigDecimal bestTakeTime,
                                         Integer rankChange, BigDecimal takeTimeChangePct,
                                         Integer commitDelta, ChangeState changeState, Instant observedAt) {
        var snapshot = new RankingSnapshot();
        snapshot.pollRunId = pollRunId;
        snapshot.contestId = contestId;
        snapshot.topic = topic;
        snapshot.teamName = teamName;
        snapshot.unit = unit;
        snapshot.present = present;
        snapshot.ranking = ranking;
        snapshot.takeTime = takeTime;
        snapshot.commitTimes = commitTimes;
        snapshot.lastCommitAt = lastCommitAt;
        snapshot.fastest = fastest;
        snapshot.bestTakeTime = bestTakeTime;
        snapshot.rankChange = rankChange;
        snapshot.takeTimeChangePct = takeTimeChangePct;
        snapshot.commitDelta = commitDelta;
        snapshot.changeState = changeState;
        snapshot.observedAt = observedAt;
        return snapshot;
    }

    public Long getId() { return id; }
    public UUID getPollRunId() { return pollRunId; }
    public String getContestId() { return contestId; }
    public String getTopic() { return topic; }
    public String getTeamName() { return teamName; }
    public String getUnit() { return unit; }
    public boolean isPresent() { return present; }
    public Integer getRanking() { return ranking; }
    public BigDecimal getTakeTime() { return takeTime; }
    public Integer getCommitTimes() { return commitTimes; }
    public Instant getLastCommitAt() { return lastCommitAt; }
    public boolean isFastest() { return fastest; }
    public BigDecimal getBestTakeTime() { return bestTakeTime; }
    public Integer getRankChange() { return rankChange; }
    public BigDecimal getTakeTimeChangePct() { return takeTimeChangePct; }
    public Integer getCommitDelta() { return commitDelta; }
    public ChangeState getChangeState() { return changeState; }
    public Instant getObservedAt() { return observedAt; }
}
