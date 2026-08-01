package com.ascend.monitor.client;

import java.util.List;

public record RankPage(
        Integer pageNo,
        Integer pageSize,
        Integer pages,
        Integer totalCount,
        List<RankEntry> list,
        Integer startSort
) {
}
