package com.wanderpool.be.party.client;

public record PointRefundCommand(
        Long memberId,
        int amount,
        Long partyId,
        Long participantId,
        String requestId
) {

    public PointRefundCommand {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (partyId == null || partyId <= 0) {
            throw new IllegalArgumentException("partyId must be positive");
        }
        if (participantId == null || participantId <= 0) {
            throw new IllegalArgumentException("participantId must be positive");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
    }

    public static PointRefundCommand cancelParticipation(
            Long memberId,
            int amount,
            Long partyId,
            Long participantId
    ) {
        return new PointRefundCommand(
                memberId,
                amount,
                partyId,
                participantId,
                "party-cancel:%d:%d".formatted(partyId, participantId)
        );
    }

    public static PointRefundCommand rejectParticipation(
            Long memberId,
            int amount,
            Long partyId,
            Long participantId
    ) {
        return new PointRefundCommand(
                memberId,
                amount,
                partyId,
                participantId,
                "party-reject:%d:%d".formatted(partyId, participantId)
        );
    }
}
