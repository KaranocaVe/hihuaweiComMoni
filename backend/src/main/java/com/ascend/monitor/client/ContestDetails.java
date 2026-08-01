package com.ascend.monitor.client;

public record ContestDetails(
        String gameId,
        String gameName,
        Integer gameStatus,
        Integer rankType,
        Integer teamRule,
        Integer teamNum,
        Integer applyNum,
        String submitDeadline
) {
}
