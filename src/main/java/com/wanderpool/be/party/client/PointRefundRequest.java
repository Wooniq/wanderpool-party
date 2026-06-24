package com.wanderpool.be.party.client;

import java.util.Objects;

public record PointRefundRequest(
        int amount,
        String requestId,
        Long partyId,
        Long participantId
) {

    public static PointRefundRequest from(PointRefundCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return new PointRefundRequest(
                command.amount(),
                command.requestId(),
                command.partyId(),
                command.participantId()
        );
    }
}
