package com.ascend.monitor.api;

import static com.ascend.monitor.api.ApiModels.*;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard/summary")
    SummaryResponse summary() {
        return dashboardService.summary();
    }

    @GetMapping("/rankings")
    PagedResponse<RankingRow> rankings(@RequestParam String topic,
                                      @RequestParam(defaultValue = "") String teamName,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return dashboardService.rankings(topic, teamName, page, size);
    }

    @GetMapping("/teams/search")
    TeamSearchResponse searchTeams(@RequestParam(defaultValue = "") String q) {
        return dashboardService.searchTeams(q);
    }

    @GetMapping("/history")
    HistoryResponse history(@RequestParam String topic,
                            @RequestParam String teamName,
                            @RequestParam(defaultValue = "168") int hours) {
        return dashboardService.history(topic, teamName, hours);
    }

    @GetMapping("/anomalies")
    SimplePage<AnomalyDto> anomalies(@RequestParam(required = false) String topic,
                                     @RequestParam(required = false) String teamName,
                                     @RequestParam(required = false) String type,
                                     @RequestParam(defaultValue = "24") int hours,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return dashboardService.anomalies(topic, teamName, type, hours, page, size);
    }

    @GetMapping("/polls")
    List<PollRunDto> polls() {
        return dashboardService.pollRuns();
    }
}
