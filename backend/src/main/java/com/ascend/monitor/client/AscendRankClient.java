package com.ascend.monitor.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import com.ascend.monitor.config.MonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AscendRankClient {

    private static final Logger log = LoggerFactory.getLogger(AscendRankClient.class);
    private static final String DETAILS = "/devCenter/contested/enrollment/details";
    private static final String TOPICS = "/devCenter/contested/enrollment/getTopicHeader";
    private static final String PERFORMANCE_RANK = "/devCenter/contested/enrollment/getWorkPerformancesRankList";

    private final RestClient restClient;
    private final MonitorProperties properties;

    public AscendRankClient(RestClient ascendRestClient, MonitorProperties properties) {
        this.restClient = ascendRestClient;
        this.properties = properties;
    }

    public ContestDetails getContestDetails() {
        var response = execute(() -> restClient.get()
                .uri(uri -> uri.path(DETAILS)
                        .queryParam("gameId", properties.contestId())
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<ContestDetails>>() {}), "赛事详情");
        return requireSuccess(response, "赛事详情");
    }

    public List<String> getTopics() {
        var response = execute(() -> restClient.get()
                .uri(uri -> uri.path(TOPICS)
                        .queryParam("gameId", properties.contestId())
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<String>>>() {}), "赛题列表");
        return requireSuccess(response, "赛题列表");
    }

    public List<RankEntry> getAllPerformanceRanks(String topic) {
        var first = getPerformanceRankPage(topic, 1);
        var pageCount = Math.max(1, first.pages() == null ? 1 : first.pages());
        if (pageCount > 100) {
            throw new SourceApiException("榜单页数异常: " + pageCount);
        }

        var deduplicated = new LinkedHashMap<String, RankEntry>();
        addEntries(deduplicated, first.list());
        for (int page = 2; page <= pageCount; page++) {
            addEntries(deduplicated, getPerformanceRankPage(topic, page).list());
        }
        log.info("Fetched topic={} rows={} pages={}", topic, deduplicated.size(), pageCount);
        return new ArrayList<>(deduplicated.values());
    }

    private RankPage getPerformanceRankPage(String topic, int pageNo) {
        var response = execute(() -> restClient.get()
                .uri(uri -> uri.path(PERFORMANCE_RANK)
                        .queryParam("gameId", properties.contestId())
                        .queryParam("teamName", "")
                        .queryParam("pageNo", pageNo)
                        .queryParam("pageSize", properties.pageSize())
                        .queryParam("topic", topic)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<RankPage>>() {}), "榜单 " + topic + " 第 " + pageNo + " 页");
        return requireSuccess(response, "榜单 " + topic + " 第 " + pageNo + " 页");
    }

    private static void addEntries(LinkedHashMap<String, RankEntry> target, List<RankEntry> entries) {
        if (entries == null) {
            return;
        }
        for (var entry : entries) {
            if (entry.teamName() == null || entry.teamName().isBlank()) {
                continue;
            }
            target.put(entry.teamName().trim().toLowerCase(Locale.ROOT), entry);
        }
    }

    private static <T> T requireSuccess(ApiResponse<T> response, String label) {
        if (response == null) {
            throw new SourceApiException(label + "返回空响应");
        }
        if (!response.isSuccessful()) {
            throw new SourceApiException(label + "失败: code=" + response.code() + ", msg=" + response.msg());
        }
        return response.data();
    }

    private static <T> T execute(Request<T> request, String label) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return request.call();
            } catch (RuntimeException error) {
                lastError = error;
                if (attempt < 3) {
                    try {
                        Thread.sleep(250L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new SourceApiException(label + "请求被中断", interrupted);
                    }
                }
            }
        }
        throw new SourceApiException(label + "连续三次请求失败", lastError);
    }

    @FunctionalInterface
    private interface Request<T> {
        T call();
    }
}
