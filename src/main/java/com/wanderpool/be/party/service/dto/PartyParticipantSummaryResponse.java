package com.wanderpool.be.party.service.dto;

import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.domain.PartyParticipant;

public record PartyParticipantSummaryResponse(
        Long participantId,
        String pickupName,
        ParticipantStatus status
) {
    public static PartyParticipantSummaryResponse from(PartyParticipant participant) {
        return new PartyParticipantSummaryResponse(
                participant.getId(),
                participant.getPickupName(),
                participant.getStatus()
        );
    }
}
