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
@Table(name = "ranking_current")
public class CurrentRanking implements RankingState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long snapshotId;

    private UUID pollRunId;

    @Column(nullable = false, length = 64)
    private String contestId;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(nullable = false)
    private String teamKey;

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

    protected CurrentRanking() {
    }

    public static CurrentRanking create(String teamKey, RankingSnapshot event) {
        var current = new CurrentRanking();
        current.teamKey = teamKey;
        current.apply(event);
        return current;
    }

    public void apply(RankingSnapshot event) {
        this.snapshotId = event.getId();
        this.pollRunId = event.getPollRunId();
        this.contestId = event.getContestId();
        this.topic = event.getTopic();
        this.teamName = event.getTeamName();
        this.unit = event.getUnit();
        this.present = event.isPresent();
        this.ranking = event.getRanking();
        this.takeTime = event.getTakeTime();
        this.commitTimes = event.getCommitTimes();
        this.lastCommitAt = event.getLastCommitAt();
        this.fastest = event.isFastest();
        this.bestTakeTime = event.getBestTakeTime();
        this.rankChange = event.getRankChange();
        this.takeTimeChangePct = event.getTakeTimeChangePct();
        this.commitDelta = event.getCommitDelta();
        this.changeState = event.getChangeState();
        this.observedAt = event.getObservedAt();
    }

    public Long getId() { return id; }
    public Long getSnapshotId() { return snapshotId; }
    public UUID getPollRunId() { return pollRunId; }
    public String getContestId() { return contestId; }
    public String getTopic() { return topic; }
    public String getTeamKey() { return teamKey; }
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
