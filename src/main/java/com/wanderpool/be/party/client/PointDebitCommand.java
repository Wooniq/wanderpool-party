package com.wanderpool.be.party.client;

public record PointDebitCommand(
        Long memberId,
        int amount,
        Long partyId,
        Long participantId,
        String requestId
) {

    public PointDebitCommand {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (partyId == null || partyId <= 0) {
            throw new IllegalArgumentException("partyId must be positive");
        }
        if (participantId != null && participantId <= 0) {
            throw new IllegalArgumentException("participantId must be positive when provided");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
    }

    public static PointDebitCommand joinParty(Long memberId, int amount, Long partyId) {
        return new PointDebitCommand(
                memberId,
                amount,
                partyId,
                null,
                "party-join:%d:%d".formatted(partyId, memberId)
        );
    }
}
