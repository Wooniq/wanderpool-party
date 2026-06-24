package com.wanderpool.be.party.grpc;

import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.PartyParticipant;
import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.domain.PartyWaypoint;
import com.wanderpool.be.grpc.party.GetPartyPassengerStopsRequest;
import com.wanderpool.be.grpc.party.GetPartyPassengerStopsResponse;
import com.wanderpool.be.grpc.party.GetPartyRouteRequest;
import com.wanderpool.be.grpc.party.GetPartyRouteResponse;
import com.wanderpool.be.grpc.party.PartyRouteServiceGrpc;
import com.wanderpool.be.grpc.party.PartyRouteStop;
import com.wanderpool.be.grpc.party.PassengerStop;
import com.wanderpool.be.grpc.party.RouteStopType;
import com.wanderpool.be.grpc.party.ValidateDriverLocationUploadRequest;
import com.wanderpool.be.grpc.party.ValidateLocationUploadResponse;
import com.wanderpool.be.grpc.party.ValidatePassengerLocationUploadRequest;
import com.wanderpool.be.party.repository.PartyParticipantRepository;
import com.wanderpool.be.party.repository.PartyRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

@GrpcService
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyMapGrpcService extends PartyRouteServiceGrpc.PartyRouteServiceImplBase {

    private final PartyRepository partyRepository;
    private final PartyParticipantRepository participantRepository;

    @Override
    public void getPartyRoute(
            GetPartyRouteRequest request,
            StreamObserver<GetPartyRouteResponse> responseObserver
    ) {
        Party party = partyRepository.findById(request.getPartyId()).orElse(null);
        if (party == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("party not found")
                    .asRuntimeException());
            return;
        }

        GetPartyRouteResponse.Builder response = GetPartyRouteResponse.newBuilder()
                .setPartyId(party.getId());

        response.addStops(PartyRouteStop.newBuilder()
                .setType(RouteStopType.ROUTE_STOP_TYPE_ORIGIN)
                .setName(toGrpcString(party.getOriginName()))
                .setLatitude(party.getOriginLat())
                .setLongitude(party.getOriginLng())
                .setOrderIndex(0)
                .build());

        party.getWaypoints().stream()
                .sorted(Comparator.comparing(PartyWaypoint::getOrderIndex))
                .forEach(waypoint -> response.addStops(PartyRouteStop.newBuilder()
                        .setType(RouteStopType.ROUTE_STOP_TYPE_WAYPOINT)
                        .setName(toGrpcString(waypoint.getName()))
                        .setLatitude(waypoint.getLatitude())
                        .setLongitude(waypoint.getLongitude())
                        .setOrderIndex(waypoint.getOrderIndex())
                        .build()));

        response.addStops(PartyRouteStop.newBuilder()
                .setType(RouteStopType.ROUTE_STOP_TYPE_DESTINATION)
                .setName(toGrpcString(party.getDestinationName()))
                .setLatitude(party.getDestLat())
                .setLongitude(party.getDestLng())
                .setOrderIndex(party.getWaypoints().size() + 1)
                .build());

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void validateDriverLocationUpload(
            ValidateDriverLocationUploadRequest request,
            StreamObserver<ValidateLocationUploadResponse> responseObserver
    ) {
        Party party = partyRepository.findById(request.getPartyId()).orElse(null);
        if (party == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("party not found")
                    .asRuntimeException());
            return;
        }

        if (!party.isDriver(request.getMemberId())) {
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription("driver member mismatch")
                    .asRuntimeException());
            return;
        }

        if (party.getStatus() != PartyStatus.IN_PROGRESS) {
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription("party is not in progress")
                    .asRuntimeException());
            return;
        }

        responseObserver.onNext(ValidateLocationUploadResponse.newBuilder()
                .setPartyId(request.getPartyId())
                .setMemberId(request.getMemberId())
                .setAllowed(true)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void validatePassengerLocationUpload(
            ValidatePassengerLocationUploadRequest request,
            StreamObserver<ValidateLocationUploadResponse> responseObserver
    ) {
        Party party = partyRepository.findById(request.getPartyId()).orElse(null);
        if (party == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("party not found")
                    .asRuntimeException());
            return;
        }

        if (party.getStatus() != PartyStatus.IN_PROGRESS) {
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription("party is not in progress")
                    .asRuntimeException());
            return;
        }

        boolean acceptedPassenger = participantRepository.findAllByPartyIdAndStatusOrderByIdAsc(
                        request.getPartyId(),
                        ParticipantStatus.ACCEPTED
                )
                .stream()
                .anyMatch(participant -> participant.getMemberId() != null
                        && participant.getMemberId().equals(request.getMemberId()));

        if (!acceptedPassenger) {
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription("passenger is not accepted participant")
                    .asRuntimeException());
            return;
        }

        responseObserver.onNext(ValidateLocationUploadResponse.newBuilder()
                .setPartyId(request.getPartyId())
                .setMemberId(request.getMemberId())
                .setAllowed(true)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getPartyPassengerStops(
            GetPartyPassengerStopsRequest request,
            StreamObserver<GetPartyPassengerStopsResponse> responseObserver
    ) {
        if (!partyRepository.existsById(request.getPartyId())) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("party not found")
                    .asRuntimeException());
            return;
        }

        GetPartyPassengerStopsResponse.Builder response = GetPartyPassengerStopsResponse.newBuilder()
                .setPartyId(request.getPartyId());

        participantRepository.findAllByPartyIdAndStatusOrderByIdAsc(
                        request.getPartyId(),
                        ParticipantStatus.ACCEPTED
                )
                .stream()
                .filter(this::hasStopCoordinates)
                .map(this::toPassengerStop)
                .forEach(response::addPassengerStops);

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private boolean hasStopCoordinates(PartyParticipant participant) {
        return participant.getPickupLat() != null
                && participant.getPickupLng() != null
                && participant.getDropoffLat() != null
                && participant.getDropoffLng() != null;
    }

    private PassengerStop toPassengerStop(PartyParticipant participant) {
        return PassengerStop.newBuilder()
                .setParticipantId(participant.getId())
                .setMemberId(participant.getMemberId())
                .setPickupName(toGrpcString(participant.getPickupName()))
                .setPickupLatitude(participant.getPickupLat())
                .setPickupLongitude(participant.getPickupLng())
                .setDropoffName(toGrpcString(participant.getDropoffName()))
                .setDropoffLatitude(participant.getDropoffLat())
                .setDropoffLongitude(participant.getDropoffLng())
                .build();
    }

    private String toGrpcString(String value) {
        return value == null ? "" : value;
    }
}
