package com.ascend.monitor.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "poll_run")
public class PollRun {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String contestId;

    private String contestName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PollStatus status;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;
    private int topicCount;
    private int snapshotCount;
    private int changedCount;
    private int anomalyCount;

    @Column(columnDefinition = "text")
    private String errorMessage;

    protected PollRun() {
    }

    public static PollRun start(String contestId, Instant now) {
        var run = new PollRun();
        run.id = UUID.randomUUID();
        run.contestId = contestId;
        run.status = PollStatus.RUNNING;
        run.startedAt = now;
        return run;
    }

    public void complete(String contestName, int topicCount, int snapshotCount,
                         int changedCount, int anomalyCount, Instant now) {
        this.contestName = contestName;
        this.topicCount = topicCount;
        this.snapshotCount = snapshotCount;
        this.changedCount = changedCount;
        this.anomalyCount = anomalyCount;
        this.completedAt = now;
        this.status = PollStatus.SUCCESS;
        this.errorMessage = null;
    }

    public void fail(Throwable error, Instant now) {
        this.completedAt = now;
        this.status = PollStatus.FAILED;
        var message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        this.errorMessage = message.length() > 4000 ? message.substring(0, 4000) : message;
    }

    public UUID getId() { return id; }
    public String getContestId() { return contestId; }
    public String getContestName() { return contestName; }
    public PollStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getTopicCount() { return topicCount; }
    public int getSnapshotCount() { return snapshotCount; }
    public int getChangedCount() { return changedCount; }
    public int getAnomalyCount() { return anomalyCount; }
    public String getErrorMessage() { return errorMessage; }
}
