package com.wanderpool.be.party.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointCreditRequestTest {

    @Test
    void from_mapsCommandFieldsExceptMemberIdInPath() {
        PointCreditCommand command = new PointCreditCommand(
                10L,
                1500,
                20L,
                "party-complete:20:driver-credit"
        );

        PointCreditRequest request = PointCreditRequest.from(command);

        assertThat(request.amount()).isEqualTo(1500);
        assertThat(request.requestId()).isEqualTo("party-complete:20:driver-credit");
        assertThat(request.partyId()).isEqualTo(20L);
    }

    @Test
    void from_rejectsNullCommand() {
        assertThatThrownBy(() -> PointCreditRequest.from(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }
}
