package com.wanderpool.be.party.client;

import java.util.Objects;

public record PointCreditRequest(
        int amount,
        String requestId,
        Long partyId
) {

    public static PointCreditRequest from(PointCreditCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return new PointCreditRequest(
                command.amount(),
                command.requestId(),
                command.partyId()
        );
    }
}
