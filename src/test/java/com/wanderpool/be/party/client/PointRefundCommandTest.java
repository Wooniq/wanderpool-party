package com.wanderpool.be.party.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointRefundCommandTest {

    @Test
    void cancelParticipation_buildsIdempotentRequestId() {
        PointRefundCommand command = PointRefundCommand.cancelParticipation(10L, 1500, 20L, 30L);

        assertThat(command.memberId()).isEqualTo(10L);
        assertThat(command.amount()).isEqualTo(1500);
        assertThat(command.partyId()).isEqualTo(20L);
        assertThat(command.participantId()).isEqualTo(30L);
        assertThat(command.requestId()).isEqualTo("party-cancel:20:30");
    }

    @Test
    void rejectParticipation_buildsIdempotentRequestId() {
        PointRefundCommand command = PointRefundCommand.rejectParticipation(10L, 1500, 20L, 30L);

        assertThat(command.memberId()).isEqualTo(10L);
        assertThat(command.amount()).isEqualTo(1500);
        assertThat(command.partyId()).isEqualTo(20L);
        assertThat(command.participantId()).isEqualTo(30L);
        assertThat(command.requestId()).isEqualTo("party-reject:20:30");
    }

    @Test
    void constructor_rejectsInvalidValues() {
        assertThatThrownBy(() -> new PointRefundCommand(null, 1, 1L, 1L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId must be positive");
        assertThatThrownBy(() -> new PointRefundCommand(0L, 1, 1L, 1L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId must be positive");
        assertThatThrownBy(() -> new PointRefundCommand(-1L, 1, 1L, 1L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId must be positive");
        assertThatThrownBy(() -> new PointRefundCommand(1L, 0, 1L, 1L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be positive");
        assertThatThrownBy(() -> new PointRefundCommand(1L, -1, 1L, 1L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be positive");
        assertThatThrownBy(() -> new PointRefundCommand(1L, 1, null, 1L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partyId must be positive");
        assertThatThrownBy(() -> new PointRefundCommand(1L, 1, 0L, 1L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partyId must be positive");
        assertThatThrownBy(() -> new PointRefundCommand(1L, 1, -1L, 1L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partyId must be positive");
        assertThatThrownBy(() -> new PointRefundCommand(1L, 1, 1L, null, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("participantId must be positive");
        assertThatThrownBy(() -> new PointRefundCommand(1L, 1, 1L, 0L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("participantId must be positive");
        assertThatThrownBy(() -> new PointRefundCommand(1L, 1, 1L, -1L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("participantId must be positive");
        assertThatThrownBy(() -> new PointRefundCommand(1L, 1, 1L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestId is required");
        assertThatThrownBy(() -> new PointRefundCommand(1L, 1, 1L, 1L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestId is required");
    }
}
