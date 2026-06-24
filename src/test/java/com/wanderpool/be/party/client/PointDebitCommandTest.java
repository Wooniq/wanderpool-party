package com.wanderpool.be.party.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointDebitCommandTest {

    @Test
    void joinParty_buildsIdempotentRequestId() {
        PointDebitCommand command = PointDebitCommand.joinParty(10L, 1500, 20L);

        assertThat(command.memberId()).isEqualTo(10L);
        assertThat(command.amount()).isEqualTo(1500);
        assertThat(command.partyId()).isEqualTo(20L);
        assertThat(command.participantId()).isNull();
        assertThat(command.requestId()).isEqualTo("party-join:20:10");
    }

    @Test
    void constructor_rejectsInvalidValues() {
        assertThatThrownBy(() -> new PointDebitCommand(null, 1, 1L, null, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId must be positive");
        assertThatThrownBy(() -> new PointDebitCommand(1L, 0, 1L, null, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be positive");
        assertThatThrownBy(() -> new PointDebitCommand(1L, 1, 0L, null, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partyId must be positive");
        assertThatThrownBy(() -> new PointDebitCommand(1L, 1, 1L, 0L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("participantId must be positive when provided");
        assertThatThrownBy(() -> new PointDebitCommand(1L, 1, 1L, null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestId is required");
    }
}
