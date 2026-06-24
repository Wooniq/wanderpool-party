package com.wanderpool.be.party.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointRefundRequestTest {

    @Test
    void from_mapsCommandFieldsExceptMemberIdInPath() {
        PointRefundCommand command = new PointRefundCommand(10L, 1500, 20L, 30L, "party-cancel:20:30");

        PointRefundRequest request = PointRefundRequest.from(command);

        assertThat(request.amount()).isEqualTo(1500);
        assertThat(request.requestId()).isEqualTo("party-cancel:20:30");
        assertThat(request.partyId()).isEqualTo(20L);
        assertThat(request.participantId()).isEqualTo(30L);
    }

    @Test
    void from_rejectsNullCommand() {
        assertThatThrownBy(() -> PointRefundRequest.from(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }
}
