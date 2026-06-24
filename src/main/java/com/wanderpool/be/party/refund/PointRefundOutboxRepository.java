package com.wanderpool.be.party.refund;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PointRefundOutboxRepository extends JpaRepository<PointRefundOutbox, Long> {

    Optional<PointRefundOutbox> findByRequestId(String requestId);

    List<PointRefundOutbox> findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
            Collection<PointRefundOutboxStatus> statuses,
            LocalDateTime now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PointRefundOutbox outbox
            set outbox.status = com.wanderpool.be.party.refund.PointRefundOutboxStatus.PROCESSING,
                outbox.attemptCount = outbox.attemptCount + 1,
                outbox.updatedAt = :now
            where outbox.id = :id
              and outbox.status in :statuses
              and outbox.nextRetryAt <= :now
            """)
    int claimReady(
            @Param("id") Long id,
            @Param("statuses") Collection<PointRefundOutboxStatus> statuses,
            @Param("now") LocalDateTime now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PointRefundOutbox outbox
            set outbox.status = com.wanderpool.be.party.refund.PointRefundOutboxStatus.FAILED_RETRYABLE,
                outbox.updatedAt = :now,
                outbox.nextRetryAt = :now
            where outbox.status = com.wanderpool.be.party.refund.PointRefundOutboxStatus.PROCESSING
              and outbox.updatedAt <= :staleBefore
            """)
    int requeueStaleProcessing(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("now") LocalDateTime now
    );
}
