package com.ascend.monitor.polling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ascend.monitor.client.ContestDetails;
import com.ascend.monitor.client.RankEntry;
import com.ascend.monitor.detection.DetectionEngine;
import com.ascend.monitor.domain.ChangeState;
import com.ascend.monitor.domain.CurrentRanking;
import com.ascend.monitor.domain.PollRun;
import com.ascend.monitor.domain.RankingSnapshot;
import com.ascend.monitor.repository.AnomalyEventRepository;
import com.ascend.monitor.repository.CurrentRankingRepository;
import com.ascend.monitor.repository.PollRunRepository;
import com.ascend.monitor.repository.RankingSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PollPersistenceServiceTest {

    private PollRunRepository pollRunRepository;
    private RankingSnapshotRepository snapshotRepository;
    private CurrentRankingRepository currentRepository;
    private AnomalyEventRepository anomalyRepository;
    private DetectionEngine detectionEngine;
    private PollPersistenceService service;

    @BeforeEach
    void setUp() {
        pollRunRepository = mock(PollRunRepository.class);
        snapshotRepository = mock(RankingSnapshotRepository.class);
        currentRepository = mock(CurrentRankingRepository.class);
        anomalyRepository = mock(AnomalyEventRepository.class);
        detectionEngine = mock(DetectionEngine.class);
        service = new PollPersistenceService(pollRunRepository, snapshotRepository,
                currentRepository, anomalyRepository, detectionEngine);
        when(detectionEngine.evaluate(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(snapshotRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<RankingSnapshot> events = invocation.getArgument(0);
            long id = 100;
            for (var event : events) {
                ReflectionTestUtils.setField(event, "id", id++);
            }
            return events;
        });
    }

    @Test
    void unchangedPollStoresTimelineButNoDuplicateTeamState() {
        var previous = current("team", "unit-a", false);
        when(currentRepository.findByContestIdAndTopic("contest", "Concat"))
                .thenReturn(List.of(previous));
        var run = run();

        var outcome = service.persist(run.getId(), contest(),
                Map.of("Concat", List.of(entry("team", "unit-a", false))), Instant.now());

        assertThat(outcome.snapshots()).isEqualTo(1);
        assertThat(outcome.changed()).isZero();
        assertThat(outcome.storedEvents()).isZero();
        var events = PollPersistenceServiceTest.<RankingSnapshot>listCaptor();
        verify(snapshotRepository).saveAll(events.capture());
        assertThat(events.getValue()).isEmpty();
        var currentRows = PollPersistenceServiceTest.<CurrentRanking>listCaptor();
        verify(currentRepository).saveAll(currentRows.capture());
        assertThat(currentRows.getValue()).isEmpty();
    }

    @Test
    void unitOnlyChangeIsPreservedAsAnEvent() {
        var previous = current("team", "unit-a", false);
        when(currentRepository.findByContestIdAndTopic("contest", "Concat"))
                .thenReturn(List.of(previous));
        var run = run();

        var outcome = service.persist(run.getId(), contest(),
                Map.of("Concat", List.of(entry("team", "unit-b", false))), Instant.now());

        assertThat(outcome.storedEvents()).isEqualTo(1);
        assertThat(outcome.changed()).isEqualTo(1);
        var events = PollPersistenceServiceTest.<RankingSnapshot>listCaptor();
        verify(snapshotRepository).saveAll(events.capture());
        assertThat(events.getValue()).singleElement().satisfies(event -> {
            assertThat(event.getChangeState()).isEqualTo(ChangeState.CHANGED);
            assertThat(event.getUnit()).isEqualTo("unit-b");
        });
        assertThat(previous.getUnit()).isEqualTo("unit-b");
        assertThat(previous.getSnapshotId()).isEqualTo(100L);
    }

    @Test
    void droppedTeamCreatesOneLosslessTransition() {
        var previous = current("team", "unit-a", false);
        when(currentRepository.findByContestIdAndTopic("contest", "Concat"))
                .thenReturn(List.of(previous));
        var run = run();

        var outcome = service.persist(run.getId(), contest(), Map.of("Concat", List.of()), Instant.now());

        assertThat(outcome.snapshots()).isEqualTo(1);
        assertThat(outcome.storedEvents()).isEqualTo(1);
        var events = PollPersistenceServiceTest.<RankingSnapshot>listCaptor();
        verify(snapshotRepository).saveAll(events.capture());
        assertThat(events.getValue()).singleElement().satisfies(event -> {
            assertThat(event.getChangeState()).isEqualTo(ChangeState.DROPPED);
            assertThat(event.isPresent()).isFalse();
        });
        assertThat(previous.isPresent()).isFalse();
    }

    private PollRun run() {
        var run = PollRun.start("contest", Instant.now());
        when(pollRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
        return run;
    }

    private static ContestDetails contest() {
        return new ContestDetails("contest", "Contest", 1, 2, 1, 1, 1, null);
    }

    private static RankEntry entry(String team, String unit, boolean fastest) {
        return new RankEntry(team, unit, 1, "2026/08/01 12:00:00",
                new BigDecimal("100.000000"), 10, null, fastest);
    }

    private static CurrentRanking current(String team, String unit, boolean fastest) {
        var observedAt = Instant.parse("2026-08-01T04:00:00Z");
        var lastCommitAt = LocalDateTime.parse("2026-08-01T12:00:00")
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        var snapshot = RankingSnapshot.create(
                UUID.randomUUID(), "contest", "Concat", team, unit, true,
                1, new BigDecimal("100.000000"), 10, lastCommitAt, fastest,
                new BigDecimal("100.000000"), null, null, null,
                ChangeState.NEW, observedAt);
        ReflectionTestUtils.setField(snapshot, "id", 1L);
        return CurrentRanking.create(team.toLowerCase(), snapshot);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<List<T>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
