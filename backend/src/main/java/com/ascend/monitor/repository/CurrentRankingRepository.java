package com.ascend.monitor.repository;

import java.util.List;

import com.ascend.monitor.domain.CurrentRanking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CurrentRankingRepository extends JpaRepository<CurrentRanking, Long> {

    List<CurrentRanking> findByContestId(String contestId);

    List<CurrentRanking> findByContestIdAndTopic(String contestId, String topic);

    Page<CurrentRanking> findByContestIdAndTopicAndPresentTrueAndTeamNameContainingIgnoreCase(
            String contestId, String topic, String teamName, Pageable pageable);

    @Query("""
            select distinct r.teamName from CurrentRanking r
            where r.contestId = :contestId
              and lower(r.teamName) like lower(concat('%', :query, '%'))
            order by r.teamName
            """)
    List<String> searchTeamNames(@Param("contestId") String contestId,
                                 @Param("query") String query,
                                 Pageable pageable);
}
