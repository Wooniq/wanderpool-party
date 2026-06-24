package com.wanderpool.be.party.service.dto;

import jakarta.validation.constraints.Size;

public record CancelParticipationRequest(
        @Size(max = 200)
        String cancelReason
) {}
