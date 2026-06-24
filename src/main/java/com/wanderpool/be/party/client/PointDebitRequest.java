package com.wanderpool.be.party.client;

import java.util.Objects;

public record PointDebitRequest(
        int amount,
        String requestId,
        Long partyId,
        Long participantId
) {

    public static PointDebitRequest from(PointDebitCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return new PointDebitRequest(
                command.amount(),
                command.requestId(),
                command.partyId(),
                command.participantId()
        );
    }
}
