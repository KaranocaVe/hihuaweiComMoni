package com.ascend.monitor.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.ascend.monitor.domain.AnomalyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;

public interface AnomalyEventRepository extends JpaRepository<AnomalyEvent, Long>, JpaSpecificationExecutor<AnomalyEvent> {

    List<AnomalyEvent> findByPollRunId(UUID pollRunId);

    List<AnomalyEvent> findBySnapshotIdIn(Collection<Long> snapshotIds);

    long countByDetectedAtAfter(Instant cutoff);

    @Modifying
    int deleteByDetectedAtBefore(Instant cutoff);
}
