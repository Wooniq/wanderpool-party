package com.wanderpool.be.party.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointDebitRequestTest {

    @Test
    void from_mapsCommandFieldsExceptMemberIdInPath() {
        PointDebitCommand command = new PointDebitCommand(
                10L,
                1500,
                20L,
                30L,
                "party-join:20:10"
        );

        PointDebitRequest request = PointDebitRequest.from(command);

        assertThat(request.amount()).isEqualTo(1500);
        assertThat(request.requestId()).isEqualTo("party-join:20:10");
        assertThat(request.partyId()).isEqualTo(20L);
        assertThat(request.participantId()).isEqualTo(30L);
    }

    @Test
    void from_rejectsNullCommand() {
        assertThatThrownBy(() -> PointDebitRequest.from(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }
}
