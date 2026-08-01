package com.ascend.monitor.polling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.ascend.monitor.config.MonitorProperties;
import com.ascend.monitor.repository.AnomalyEventRepository;
import com.ascend.monitor.repository.PollRunRepository;
import com.ascend.monitor.repository.RankingSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final MonitorProperties properties;
    private final AnomalyEventRepository anomalyRepository;
    private final RankingSnapshotRepository snapshotRepository;
    private final PollRunRepository pollRunRepository;

    public RetentionService(MonitorProperties properties,
                            AnomalyEventRepository anomalyRepository,
                            RankingSnapshotRepository snapshotRepository,
                            PollRunRepository pollRunRepository) {
        this.properties = properties;
        this.anomalyRepository = anomalyRepository;
        this.snapshotRepository = snapshotRepository;
        this.pollRunRepository = pollRunRepository;
    }

    @Transactional
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Shanghai")
    public void cleanup() {
        var cutoff = Instant.now().minus(properties.retentionDays(), ChronoUnit.DAYS);
        int anomalies = anomalyRepository.deleteByDetectedAtBefore(cutoff);
        int snapshots = snapshotRepository.deleteObsoleteBefore(cutoff);
        int runs = pollRunRepository.deleteCompletedBefore(cutoff);
        log.info("Retention cleanup cutoff={} anomalies={} obsoleteEvents={} runs={}",
                cutoff, anomalies, snapshots, runs);
    }
}
