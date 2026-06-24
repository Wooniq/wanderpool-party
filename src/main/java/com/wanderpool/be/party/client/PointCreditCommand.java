package com.wanderpool.be.party.client;

public record PointCreditCommand(
        Long memberId,
        int amount,
        Long partyId,
        String requestId
) {

    public PointCreditCommand {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (partyId == null || partyId <= 0) {
            throw new IllegalArgumentException("partyId must be positive");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
    }

    public static PointCreditCommand completeParty(Long memberId, int amount, Long partyId) {
        return new PointCreditCommand(
                memberId,
                amount,
                partyId,
                "party-complete:%d:driver-credit".formatted(partyId)
        );
    }
}
