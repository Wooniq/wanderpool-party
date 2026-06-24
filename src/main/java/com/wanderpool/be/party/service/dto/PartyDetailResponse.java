package com.wanderpool.be.party.service.dto;

import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.domain.PartyWaypoint;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record PartyDetailResponse(
        Long partyId,
        String title,
        String description,
        PartyStatus status,
        String originName,
        Double originLat,
        Double originLng,
        LocalDateTime departureTime,
        List<PartyWaypointDetailResponse> waypoints,
        String destinationName,
        Double destLat,
        Double destLng,
        LocalDateTime arrivalTime,
        Integer maxPassengers,
        Integer currentPassengers
) {
    public static PartyDetailResponse from(Party party) {
        List<PartyWaypointDetailResponse> waypointResponses = party.getWaypoints().stream()
                .sorted(Comparator.comparing(PartyWaypoint::getOrderIndex))
                .map(PartyWaypointDetailResponse::from)
                .toList();

        return new PartyDetailResponse(
                party.getId(),
                party.getTitle(),
                party.getDescription(),
                party.getStatus(),
                party.getOriginName(),
                party.getOriginLat(),
                party.getOriginLng(),
                party.getDepartureTime(),
                waypointResponses,
                party.getDestinationName(),
                party.getDestLat(),
                party.getDestLng(),
                party.getArrivalTime(),
                party.getMaxPassengers(),
                party.getCurrentPassengers()
        );
    }

    public record PartyWaypointDetailResponse(
            Integer orderIndex,
            String name,
            Double latitude,
            Double longitude,
            LocalDateTime estimatedArrival
    ) {
        public static PartyWaypointDetailResponse from(PartyWaypoint waypoint) {
            return new PartyWaypointDetailResponse(
                    waypoint.getOrderIndex(),
                    waypoint.getName(),
                    waypoint.getLatitude(),
                    waypoint.getLongitude(),
                    waypoint.getEstimatedArrival()
            );
        }
    }
}
