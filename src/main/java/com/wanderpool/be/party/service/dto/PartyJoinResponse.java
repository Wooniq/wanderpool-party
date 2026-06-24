package com.wanderpool.be.party.service.dto;

import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.domain.PartyParticipant;

public record PartyJoinResponse(
        Long participantId,
        Long partyId,
        ParticipantStatus status,
        Integer pointCost,
        Integer currentPassengers,
        Integer maxPassengers
) {
    public static PartyJoinResponse from(PartyParticipant participant) {
        return new PartyJoinResponse(
                participant.getId(),
                participant.getParty().getId(),
                participant.getStatus(),
                participant.getPointCost(),
                participant.getParty().getCurrentPassengers(),
                participant.getParty().getMaxPassengers()
        );
    }
}
