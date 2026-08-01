package com.ascend.monitor.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ascend.monitor.domain.PollRun;
import com.ascend.monitor.domain.PollStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollRunRepository extends JpaRepository<PollRun, UUID> {

    Optional<PollRun> findTopByStatusOrderByCompletedAtDesc(PollStatus status);

    Optional<PollRun> findTopByOrderByStartedAtDesc();

    List<PollRun> findTop20ByOrderByStartedAtDesc();

    @Modifying
    @Query("delete from PollRun p where p.completedAt < :cutoff")
    int deleteCompletedBefore(@Param("cutoff") Instant cutoff);
}
