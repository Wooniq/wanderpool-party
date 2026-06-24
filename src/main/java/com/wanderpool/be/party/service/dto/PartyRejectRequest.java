package com.wanderpool.be.party.service.dto;

import jakarta.validation.constraints.Size;

public record PartyRejectRequest(
        @Size(max = 200)
        String reason
) {}
