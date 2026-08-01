package com.ascend.monitor.detection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.ascend.monitor.config.MonitorProperties;
import com.ascend.monitor.domain.AnomalyType;
import com.ascend.monitor.domain.ChangeState;
import com.ascend.monitor.domain.RankingState;
import com.ascend.monitor.domain.RankingSnapshot;
import org.springframework.stereotype.Component;

@Component
public class DetectionEngine {

    private final MonitorProperties.Detection thresholds;

    public DetectionEngine(MonitorProperties properties) {
        this.thresholds = properties.detection();
    }

    public List<AnomalyDraft> evaluate(RankingState previous, RankingSnapshot current) {
        var events = new ArrayList<AnomalyDraft>();
        if (previous == null) {
            return events;
        }

        if (current.getChangeState() == ChangeState.DROPPED) {
            events.add(new AnomalyDraft(
                    AnomalyType.DROPPED_FROM_BOARD,
                    80,
                    "队伍从榜单消失",
                    "上一轮仍在榜，本轮完整分页结果中未返回该队伍。可能是无效提交、主动降分或榜单规则变化，需要结合后续重新上榜情况判断。",
                    previous.getTakeTime(), null, previous.getBestTakeTime()));
            return events;
        }

        if (current.getChangeState() == ChangeState.RETURNED) {
            events.add(new AnomalyDraft(
                    AnomalyType.RETURNED_TO_BOARD,
                    65,
                    "队伍重新上榜",
                    "该队伍在上一轮不在榜，本轮重新出现。建议结合掉榜持续时间、当前耗时和提交次数检查是否存在隐藏成绩窗口。",
                    previous.getTakeTime(), current.getTakeTime(), previous.getBestTakeTime()));
        }

        if (!current.isPresent() || current.getTakeTime() == null || previous.getTakeTime() == null) {
            return events;
        }

        var changePct = current.getTakeTimeChangePct();
        var commitDelta = current.getCommitDelta() == null ? 0 : current.getCommitDelta();
        var priorBest = previous.getBestTakeTime();

        if (commitDelta > 0 && changePct != null
                && changePct.compareTo(BigDecimal.valueOf(thresholds.regressionPercent())) >= 0) {
            int severity = clamp(50 + changePct.intValue() / 2 + Math.min(15, commitDelta), 50, 95);
            events.add(new AnomalyDraft(
                    AnomalyType.SCORE_REGRESSION,
                    severity,
                    "提交后成绩明显下降",
                    "提交次数增加 " + commitDelta + " 次后，耗时较上一轮恶化 " + display(changePct) + "%（性能榜耗时越低越好）。",
                    previous.getTakeTime(), current.getTakeTime(), priorBest));
        }

        if (commitDelta > 0 && priorBest != null && priorBest.signum() > 0) {
            var gapFromBest = percent(current.getTakeTime().subtract(priorBest), priorBest);
            if (gapFromBest.compareTo(BigDecimal.valueOf(thresholds.hidingPercent())) >= 0) {
                int severity = clamp(60 + gapFromBest.intValue() / 3, 60, 95);
                events.add(new AnomalyDraft(
                        AnomalyType.POSSIBLE_HIDING,
                        severity,
                        "当前成绩显著低于历史最佳",
                        "本轮发生了新提交，但当前耗时比本平台已观测到的历史最佳慢 " + display(gapFromBest)
                                + "%；这是一条疑似藏分信号，不等同于违规结论。",
                        previous.getTakeTime(), current.getTakeTime(), priorBest));
            }
        }

        if (priorBest != null && priorBest.signum() > 0 && changePct != null) {
            var previousGap = percent(previous.getTakeTime().subtract(priorBest), priorBest);
            var currentGap = percent(current.getTakeTime().subtract(priorBest), priorBest);
            if (previousGap.compareTo(BigDecimal.valueOf(thresholds.hidingPercent())) >= 0
                    && changePct.compareTo(BigDecimal.valueOf(-thresholds.reboundPercent())) <= 0
                    && currentGap.compareTo(BigDecimal.valueOf(thresholds.nearBestPercent())) <= 0) {
                events.add(new AnomalyDraft(
                        AnomalyType.SCORE_REBOUND,
                        85,
                        "成绩快速回到历史最佳附近",
                        "上一轮明显偏离历史最佳，本轮耗时改善 " + display(changePct.abs())
                                + "% 并回到历史最佳的 " + display(currentGap.max(BigDecimal.ZERO)) + "% 范围内。",
                        previous.getTakeTime(), current.getTakeTime(), priorBest));
            }
        }

        if (commitDelta >= thresholds.submissionBurst()) {
            int severity = clamp(45 + commitDelta * 2, 45, 90);
            events.add(new AnomalyDraft(
                    AnomalyType.SUBMISSION_BURST,
                    severity,
                    "短周期提交次数突增",
                    "两次采集之间累计提交次数增加 " + commitDelta + " 次，超过阈值 " + thresholds.submissionBurst() + "。",
                    previous.getTakeTime(), current.getTakeTime(), priorBest));
        }

        return events;
    }

    private static BigDecimal percent(BigDecimal delta, BigDecimal base) {
        return delta.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
    }

    private static String display(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
