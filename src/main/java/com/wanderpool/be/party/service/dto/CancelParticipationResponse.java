package com.wanderpool.be.party.service.dto;

import com.wanderpool.be.domain.PartyStatus;
import java.time.LocalDateTime;

public record CancelParticipationResponse(
        Long participantId,
        Long partyId,
        int refundedPoints,
        int currentPassengers,
        PartyStatus partyStatus,
        LocalDateTime cancelledAt
) {}
