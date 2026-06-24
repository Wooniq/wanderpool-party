package com.wanderpool.be.party.service.dto;

import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.PartyStatus;
import java.time.LocalDateTime;

public record PartyCreateResponse(
        Long partyId,
        PartyStatus status,
        String title,
        String originName,
        Double originLat,
        Double originLng,
        String destinationName,
        Double destLat,
        Double destLng,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        Integer maxPassengers,
        Integer currentPassengers,
        int waypointCount
) {
    public static PartyCreateResponse from(Party party) {
        return new PartyCreateResponse(
                party.getId(),
                party.getStatus(),
                party.getTitle(),
                party.getOriginName(),
                party.getOriginLat(),
                party.getOriginLng(),
                party.getDestinationName(),
                party.getDestLat(),
                party.getDestLng(),
                party.getDepartureTime(),
                party.getArrivalTime(),
                party.getMaxPassengers(),
                party.getCurrentPassengers(),
                party.getWaypoints().size()
        );
    }
}
