package com.ascend.monitor.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.ascend.monitor.domain.RankingSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    List<RankingSnapshot> findByPollRunId(UUID pollRunId);

    List<RankingSnapshot> findByPollRunIdAndTopic(UUID pollRunId, String topic);

    Page<RankingSnapshot> findByPollRunIdAndTopicAndPresentTrueAndTeamNameContainingIgnoreCase(
            UUID pollRunId, String topic, String teamName, Pageable pageable);

    List<RankingSnapshot> findByContestIdAndTopicAndTeamNameIgnoreCaseAndObservedAtBetweenOrderByObservedAtAsc(
            String contestId, String topic, String teamName, Instant from, Instant to);

    @Query("""
            select distinct r.teamName from RankingSnapshot r
            where r.contestId = :contestId
              and lower(r.teamName) like lower(concat('%', :query, '%'))
            order by r.teamName
            """)
    List<String> searchTeamNames(@Param("contestId") String contestId,
                                 @Param("query") String query,
                                 Pageable pageable);

    @Query("select r from RankingSnapshot r where r.id in :ids")
    List<RankingSnapshot> findAllByIds(@Param("ids") Collection<Long> ids);

    @Modifying
    int deleteByObservedAtBefore(Instant cutoff);
}
