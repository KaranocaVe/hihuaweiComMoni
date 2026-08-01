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
@Table(name = "anomaly_event")
public class AnomalyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long snapshotId;

    @Column(nullable = false)
    private UUID pollRunId;

    @Column(nullable = false, length = 64)
    private String contestId;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(nullable = false)
    private String teamName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AnomalyType type;

    private int severity;
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(precision = 20, scale = 6)
    private BigDecimal previousTakeTime;

    @Column(precision = 20, scale = 6)
    private BigDecimal currentTakeTime;

    @Column(precision = 20, scale = 6)
    private BigDecimal baselineTakeTime;

    @Column(nullable = false)
    private Instant detectedAt;

    protected AnomalyEvent() {
    }

    public static AnomalyEvent create(RankingSnapshot snapshot, AnomalyType type, int severity,
                                      String title, String description, BigDecimal previousTakeTime,
                                      BigDecimal currentTakeTime, BigDecimal baselineTakeTime) {
        var event = new AnomalyEvent();
        event.snapshotId = snapshot.getId();
        event.pollRunId = snapshot.getPollRunId();
        event.contestId = snapshot.getContestId();
        event.topic = snapshot.getTopic();
        event.teamName = snapshot.getTeamName();
        event.type = type;
        event.severity = severity;
        event.title = title;
        event.description = description;
        event.previousTakeTime = previousTakeTime;
        event.currentTakeTime = currentTakeTime;
        event.baselineTakeTime = baselineTakeTime;
        event.detectedAt = snapshot.getObservedAt();
        return event;
    }

    public Long getId() { return id; }
    public Long getSnapshotId() { return snapshotId; }
    public UUID getPollRunId() { return pollRunId; }
    public String getContestId() { return contestId; }
    public String getTopic() { return topic; }
    public String getTeamName() { return teamName; }
    public AnomalyType getType() { return type; }
    public int getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getPreviousTakeTime() { return previousTakeTime; }
    public BigDecimal getCurrentTakeTime() { return currentTakeTime; }
    public BigDecimal getBaselineTakeTime() { return baselineTakeTime; }
    public Instant getDetectedAt() { return detectedAt; }
}
