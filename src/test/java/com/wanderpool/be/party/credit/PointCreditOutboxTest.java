package com.wanderpool.be.party.credit;

import com.wanderpool.be.party.client.PointCreditCommand;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointCreditOutboxTest {

    @Test
    void fromCommand_createsPendingOutbox() {
        PointCreditCommand command = PointCreditCommand.completeParty(2L, 1000, 1L);

        PointCreditOutbox outbox = PointCreditOutbox.from(command);

        assertThat(outbox.getRequestId()).isEqualTo("party-complete:1:driver-credit");
        assertThat(outbox.getMemberId()).isEqualTo(2L);
        assertThat(outbox.getAmount()).isEqualTo(1000);
        assertThat(outbox.getPartyId()).isEqualTo(1L);
        assertThat(outbox.getStatus()).isEqualTo(PointCreditOutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isZero();
        assertThat(outbox.getNextRetryAt()).isNotNull();
    }

    @Test
    void toCommand_restoresCreditCommand() {
        PointCreditCommand command = new PointCreditCommand(
                2L,
                1000,
                1L,
                "party-complete:1:driver-credit"
        );
        PointCreditOutbox outbox = PointCreditOutbox.from(command);

        assertThat(outbox.toCommand()).isEqualTo(command);
    }

    @Test
    void markSucceeded_clearsLastErrorAndSetsProcessedAt() {
        PointCreditOutbox outbox = PointCreditOutbox.from(
                PointCreditCommand.completeParty(2L, 1000, 1L)
        );
        outbox.markRetryableFailure("temporary failure", 0);

        outbox.markSucceeded();

        assertThat(outbox.getStatus()).isEqualTo(PointCreditOutboxStatus.SUCCEEDED);
        assertThat(outbox.getLastError()).isNull();
        assertThat(outbox.getProcessedAt()).isNotNull();
    }

    @Test
    void markRetryableFailure_rejectsNegativeDelay() {
        PointCreditOutbox outbox = PointCreditOutbox.from(
                PointCreditCommand.completeParty(2L, 1000, 1L)
        );

        assertThatThrownBy(() -> outbox.markRetryableFailure("failed", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retryDelaySeconds must be non-negative");
    }

    @Test
    void markRetryableFailure_truncatesLongErrorMessage() {
        PointCreditOutbox outbox = PointCreditOutbox.from(
                PointCreditCommand.completeParty(2L, 1000, 1L)
        );
        String longMessage = "x".repeat(600);

        outbox.markRetryableFailure(longMessage, 0);

        assertThat(outbox.getStatus()).isEqualTo(PointCreditOutboxStatus.FAILED_RETRYABLE);
        assertThat(outbox.getLastError()).hasSize(500);
    }

    @Test
    void markManualReview_allowsNullErrorMessage() {
        PointCreditOutbox outbox = PointCreditOutbox.from(
                PointCreditCommand.completeParty(2L, 1000, 1L)
        );

        outbox.markManualReview(null);

        assertThat(outbox.getStatus()).isEqualTo(PointCreditOutboxStatus.FAILED_MANUAL_REVIEW);
        assertThat(outbox.getLastError()).isNull();
        assertThat(outbox.getProcessedAt()).isNotNull();
    }

    @Test
    void lifecycleCallbacks_initializeAndUpdateTimestamps() {
        PointCreditOutbox outbox = PointCreditOutbox.from(
                PointCreditCommand.completeParty(2L, 1000, 1L)
        );
        ReflectionTestUtils.setField(outbox, "createdAt", null);
        ReflectionTestUtils.setField(outbox, "updatedAt", null);
        ReflectionTestUtils.setField(outbox, "nextRetryAt", null);

        outbox.prePersist();

        assertThat(outbox.getCreatedAt()).isNotNull();
        assertThat(outbox.getUpdatedAt()).isNotNull();
        assertThat(outbox.getNextRetryAt()).isNotNull();

        outbox.preUpdate();

        assertThat(outbox.getUpdatedAt()).isNotNull();
    }
}
