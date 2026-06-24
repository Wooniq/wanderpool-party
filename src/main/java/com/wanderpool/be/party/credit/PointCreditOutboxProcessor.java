package com.wanderpool.be.party.credit;

import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.client.NonRetryableMemberClientException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointCreditOutboxProcessor {

    private static final List<PointCreditOutboxStatus> READY_STATUSES = List.of(
            PointCreditOutboxStatus.PENDING,
            PointCreditOutboxStatus.FAILED_RETRYABLE
    );

    private final PointCreditOutboxRepository outboxRepository;
    private final MemberClient memberClient;

    @Value("${party.credit-outbox.max-attempts:5}")
    private int maxAttempts;

    @Value("${party.credit-outbox.retry-delay-seconds:30}")
    private long retryDelaySeconds;

    @Value("${party.credit-outbox.processing-timeout-seconds:120}")
    private long processingTimeoutSeconds;

    @Scheduled(fixedDelayString = "${party.credit-outbox.poll-delay-ms:5000}")
    public void processReadyCredits() {
        LocalDateTime now = LocalDateTime.now();
        outboxRepository.requeueStaleProcessing(now.minusSeconds(processingTimeoutSeconds), now);
        outboxRepository.findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(READY_STATUSES, now)
                .forEach(outbox -> process(outbox.getId()));
    }

    public void process(Long outboxId) {
        if (outboxRepository.claimReady(outboxId, READY_STATUSES, LocalDateTime.now()) != 1) {
            return;
        }

        PointCreditOutbox outbox = outboxRepository.findById(outboxId)
                .orElseThrow();

        try {
            memberClient.creditPoints(outbox.toCommand());
            outbox.markSucceeded();
        } catch (NonRetryableMemberClientException e) {
            outbox.markManualReview(e.getMessage());
        } catch (RuntimeException e) {
            if (outbox.getAttemptCount() >= maxAttempts) {
                outbox.markManualReview(e.getMessage());
            } else {
                outbox.markRetryableFailure(e.getMessage(), retryDelaySeconds);
            }
        }
        outboxRepository.save(outbox);
    }
}
