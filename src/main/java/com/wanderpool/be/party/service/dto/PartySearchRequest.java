package com.wanderpool.be.party.service.dto;

import com.wanderpool.be.domain.PartyStatus;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public record PartySearchRequest(
        String originName,
        String destinationName,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime departureAfter,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime arrivalBefore,
        PartyStatus status
) {
}
