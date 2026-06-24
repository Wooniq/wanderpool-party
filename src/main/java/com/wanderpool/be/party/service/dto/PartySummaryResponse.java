package com.wanderpool.be.party.service.dto;

import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.domain.PartyWaypoint;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record PartySummaryResponse(
        Long partyId,
        String title,
        String originName,
        Double originLat,
        Double originLng,
        String destinationName,
        Double destLat,
        Double destLng,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        Integer currentPassengers,
        Integer maxPassengers,
        PartyStatus status,
        List<PartyWaypointSummaryResponse> waypoints
) {
    public static PartySummaryResponse from(Party party) {
        List<PartyWaypointSummaryResponse> waypointResponses = party.getWaypoints().stream()
                .sorted(Comparator.comparing(PartyWaypoint::getOrderIndex))
                .map(PartyWaypointSummaryResponse::from)
                .toList();

        return new PartySummaryResponse(
                party.getId(),
                party.getTitle(),
                party.getOriginName(),
                party.getOriginLat(),
                party.getOriginLng(),
                party.getDestinationName(),
                party.getDestLat(),
                party.getDestLng(),
                party.getDepartureTime(),
                party.getArrivalTime(),
                party.getCurrentPassengers(),
                party.getMaxPassengers(),
                party.getStatus(),
                waypointResponses
        );
    }

    public record PartyWaypointSummaryResponse(
            Integer orderIndex,
            String name,
            Double latitude,
            Double longitude
    ) {
        public static PartyWaypointSummaryResponse from(PartyWaypoint waypoint) {
            return new PartyWaypointSummaryResponse(
                    waypoint.getOrderIndex(),
                    waypoint.getName(),
                    waypoint.getLatitude(),
                    waypoint.getLongitude()
            );
        }
    }
}
