package com.ascend.monitor.detection;

import java.math.BigDecimal;

import com.ascend.monitor.domain.AnomalyType;

public record AnomalyDraft(
        AnomalyType type,
        int severity,
        String title,
        String description,
        BigDecimal previousTakeTime,
        BigDecimal currentTakeTime,
        BigDecimal baselineTakeTime
) {
}
