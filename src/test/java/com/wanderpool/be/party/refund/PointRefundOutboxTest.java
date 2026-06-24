package com.wanderpool.be.party.refund;

import com.wanderpool.be.party.client.PointRefundCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointRefundOutboxTest {

    @Test
    void fromCommand_createsPendingOutbox() {
        PointRefundCommand command = PointRefundCommand.cancelParticipation(2L, 1000, 1L, 10L);

        PointRefundOutbox outbox = PointRefundOutbox.from(command);

        assertThat(outbox.getRequestId()).isEqualTo("party-cancel:1:10");
        assertThat(outbox.getMemberId()).isEqualTo(2L);
        assertThat(outbox.getAmount()).isEqualTo(1000);
        assertThat(outbox.getPartyId()).isEqualTo(1L);
        assertThat(outbox.getParticipantId()).isEqualTo(10L);
        assertThat(outbox.getStatus()).isEqualTo(PointRefundOutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isZero();
    }

    @Test
    void toCommand_restoresRefundCommand() {
        PointRefundCommand command = new PointRefundCommand(2L, 1000, 1L, 10L, "party-cancel:1:10");
        PointRefundOutbox outbox = PointRefundOutbox.from(command);

        assertThat(outbox.toCommand()).isEqualTo(command);
    }

    @Test
    void markRetryableFailure_rejectsNegativeDelay() {
        PointRefundOutbox outbox = PointRefundOutbox.from(
                PointRefundCommand.cancelParticipation(2L, 1000, 1L, 10L)
        );

        assertThatThrownBy(() -> outbox.markRetryableFailure("failed", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retryDelaySeconds must be non-negative");
    }
}
