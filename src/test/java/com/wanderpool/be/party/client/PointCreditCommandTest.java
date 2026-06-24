package com.wanderpool.be.party.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointCreditCommandTest {

    @Test
    void completeParty_buildsIdempotentRequestId() {
        PointCreditCommand command = PointCreditCommand.completeParty(10L, 1500, 20L);

        assertThat(command.memberId()).isEqualTo(10L);
        assertThat(command.amount()).isEqualTo(1500);
        assertThat(command.partyId()).isEqualTo(20L);
        assertThat(command.requestId()).isEqualTo("party-complete:20:driver-credit");
    }

    @Test
    void constructor_rejectsInvalidValues() {
        assertThatThrownBy(() -> new PointCreditCommand(null, 1, 1L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId must be positive");
        assertThatThrownBy(() -> new PointCreditCommand(1L, 0, 1L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be positive");
        assertThatThrownBy(() -> new PointCreditCommand(1L, 1, 0L, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partyId must be positive");
        assertThatThrownBy(() -> new PointCreditCommand(1L, 1, 1L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestId is required");
    }
}
