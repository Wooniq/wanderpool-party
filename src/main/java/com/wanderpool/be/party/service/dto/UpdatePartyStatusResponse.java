package com.wanderpool.be.party.service.dto;

import com.wanderpool.be.domain.PartyStatus;

public record UpdatePartyStatusResponse(
        Long partyId,
        PartyStatus status
) {}
