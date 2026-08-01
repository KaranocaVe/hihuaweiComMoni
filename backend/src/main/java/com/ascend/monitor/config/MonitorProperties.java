package com.ascend.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor")
public record MonitorProperties(
        String contestId,
        String sourceBaseUrl,
        String sourcePageUrl,
        long pollIntervalMs,
        long initialDelayMs,
        int pageSize,
        int historyMaxPoints,
        int retentionDays,
        Detection detection
) {
    public record Detection(
            double regressionPercent,
            double hidingPercent,
            double reboundPercent,
            double nearBestPercent,
            int submissionBurst
    ) {
    }
}
