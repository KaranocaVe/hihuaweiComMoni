package com.ascend.monitor.client;

import java.math.BigDecimal;

public record RankEntry(
        String teamName,
        String unit,
        Integer ranking,
        String lastCommit,
        BigDecimal takeTime,
        Integer commitTimes,
        String url,
        Boolean fastest
) {
}
