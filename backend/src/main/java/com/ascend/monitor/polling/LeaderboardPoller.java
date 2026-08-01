package com.ascend.monitor.polling;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ascend.monitor.client.AscendRankClient;
import com.ascend.monitor.client.RankEntry;
import com.ascend.monitor.client.SourceApiException;
import com.ascend.monitor.config.MonitorProperties;
import com.ascend.monitor.domain.PollRun;
import com.ascend.monitor.repository.PollRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardPoller {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardPoller.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AscendRankClient rankClient;
    private final MonitorProperties properties;
    private final PollRunRepository pollRunRepository;
    private final PollPersistenceService persistenceService;

    public LeaderboardPoller(AscendRankClient rankClient, MonitorProperties properties,
                             PollRunRepository pollRunRepository,
                             PollPersistenceService persistenceService) {
        this.rankClient = rankClient;
        this.properties = properties;
        this.pollRunRepository = pollRunRepository;
        this.persistenceService = persistenceService;
    }

    @Scheduled(fixedRateString = "${monitor.poll-interval-ms:120000}",
            initialDelayString = "${monitor.initial-delay-ms:5000}")
    public void scheduledPoll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Previous poll is still running; skipping this cycle");
            return;
        }

        var startedAt = Instant.now();
        var run = pollRunRepository.save(PollRun.start(properties.contestId(), startedAt));
        try {
            var contest = rankClient.getContestDetails();
            if (!Integer.valueOf(2).equals(contest.rankType())) {
                throw new SourceApiException("当前赛事 rankType=" + contest.rankType() + "，本版本仅支持性能榜 rankType=2");
            }
            var topics = rankClient.getTopics();
            if (topics.isEmpty()) {
                throw new SourceApiException("赛题列表为空");
            }

            var topicEntries = new LinkedHashMap<String, List<RankEntry>>();
            for (var topic : topics) {
                topicEntries.put(topic, rankClient.getAllPerformanceRanks(topic));
            }
            var outcome = persistenceService.persist(run.getId(), contest, topicEntries, Instant.now());
            log.info("Poll complete run={} topics={} snapshots={} changed={} anomalies={}",
                    run.getId(), topics.size(), outcome.snapshots(), outcome.changed(), outcome.anomalies());
        } catch (Throwable error) {
            log.error("Poll failed run={}", run.getId(), error);
            persistenceService.markFailed(run.getId(), error, Instant.now());
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}
