package com.wanderpool.be.party.credit;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PointCreditOutboxRepository extends JpaRepository<PointCreditOutbox, Long> {

    Optional<PointCreditOutbox> findByRequestId(String requestId);

    List<PointCreditOutbox> findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
            Collection<PointCreditOutboxStatus> statuses,
            LocalDateTime now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PointCreditOutbox outbox
            set outbox.status = com.wanderpool.be.party.credit.PointCreditOutboxStatus.PROCESSING,
                outbox.attemptCount = outbox.attemptCount + 1,
                outbox.updatedAt = :now
            where outbox.id = :id
              and outbox.status in :statuses
              and outbox.nextRetryAt <= :now
            """)
    int claimReady(
            @Param("id") Long id,
            @Param("statuses") Collection<PointCreditOutboxStatus> statuses,
            @Param("now") LocalDateTime now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PointCreditOutbox outbox
            set outbox.status = com.wanderpool.be.party.credit.PointCreditOutboxStatus.FAILED_RETRYABLE,
                outbox.updatedAt = :now,
                outbox.nextRetryAt = :now
            where outbox.status = com.wanderpool.be.party.credit.PointCreditOutboxStatus.PROCESSING
              and outbox.updatedAt <= :staleBefore
            """)
    int requeueStaleProcessing(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("now") LocalDateTime now
    );
}
