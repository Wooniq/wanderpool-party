package com.wanderpool.be.party.service;

import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.PartyParticipant;
import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.domain.PartyWaypoint;
import com.wanderpool.be.party.client.InsufficientPointsMemberClientException;
import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.client.NonRetryableMemberClientException;
import com.wanderpool.be.party.client.PointCreditCommand;
import com.wanderpool.be.party.client.PointDebitCommand;
import com.wanderpool.be.party.client.PointRefundCommand;
import com.wanderpool.be.party.client.RetryableMemberClientException;
import com.wanderpool.be.party.credit.PointCreditOutbox;
import com.wanderpool.be.party.credit.PointCreditOutboxRepository;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.refund.PointRefundOutbox;
import com.wanderpool.be.party.refund.PointRefundOutboxRepository;
import com.wanderpool.be.party.repository.PartyParticipantRepository;
import com.wanderpool.be.party.repository.PartyRepository;
import com.wanderpool.be.party.service.dto.CancelParticipationResponse;
import com.wanderpool.be.party.service.dto.PartyCreateRequest;
import com.wanderpool.be.party.service.dto.PartyCreateResponse;
import com.wanderpool.be.party.service.dto.PartyDetailResponse;
import com.wanderpool.be.party.service.dto.PartyJoinRequest;
import com.wanderpool.be.party.service.dto.PartyJoinResponse;
import com.wanderpool.be.party.service.dto.PartyParticipantSummaryResponse;
import com.wanderpool.be.party.service.dto.PartyRejectRequest;
import com.wanderpool.be.party.service.dto.PartySearchRequest;
import com.wanderpool.be.party.service.dto.PartySummaryResponse;
import com.wanderpool.be.party.service.dto.UpdatePartyStatusResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PartyService {

    private final PartyRepository partyRepository;
    private final PartyParticipantRepository participantRepository;
    private final PointRefundOutboxRepository pointRefundOutboxRepository;
    private final PointCreditOutboxRepository pointCreditOutboxRepository;
    private final MemberClient memberClient;

    @Transactional
    public PartyCreateResponse createParty(Long driverMemberId, PartyCreateRequest request) {
        verifyMemberRoleIsDriver(driverMemberId);

        Party party = Party.create(
                driverMemberId,
                request.title(),
                request.description(),
                request.originName(), request.originLat(), request.originLng(),
                request.destinationName(), request.destLat(), request.destLng(),
                request.departureTime(),
                request.arrivalTime(),
                request.maxPassengers()
        );

        if (request.waypoints() != null) {
            for (PartyCreateRequest.WaypointRequest wp : request.waypoints()) {
                party.addWaypoint(PartyWaypoint.create(
                        wp.orderIndex(),
                        wp.name(),
                        wp.latitude(),
                        wp.longitude()
                ));
            }
        }

        partyRepository.save(party);
        return PartyCreateResponse.from(party);
    }

    private void verifyMemberRoleIsDriver(Long memberId) {
        try {
            String role = memberClient.getRole(memberId);
            if (!"DRIVER".equalsIgnoreCase(role)) {
                throw new PartyException(PartyErrorCode.NOT_DRIVER);
            }
        } catch (PartyException e) {
            throw e;
        } catch (RetryableMemberClientException | NonRetryableMemberClientException e) {
            throw new PartyException(PartyErrorCode.MEMBER_SERVICE_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public List<PartySummaryResponse> searchParties(PartySearchRequest request) {
        return partyRepository.findAll(partySearchSpec(request)).stream()
                .map(PartySummaryResponse::from)
                .toList();
    }

    private Specification<Party> partySearchSpec(PartySearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(request.originName())) {
                predicates.add(cb.like(root.get("originName"), "%" + request.originName() + "%"));
            }
            if (StringUtils.hasText(request.destinationName())) {
                predicates.add(cb.like(root.get("destinationName"), "%" + request.destinationName() + "%"));
            }
            if (request.departureAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("departureTime"), request.departureAfter()));
            }
            if (request.arrivalBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDateTime>get("arrivalTime"), request.arrivalBefore()));
            }
            if (request.status() != null) {
                predicates.add(cb.equal(root.get("status"), request.status()));
            }

            query.orderBy(
                    cb.asc(cb.selectCase()
                            .when(cb.equal(root.get("status"), PartyStatus.RECRUITING), 0)
                            .otherwise(1)),
                    cb.asc(root.<LocalDateTime>get("departureTime"))
            );
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public List<PartySummaryResponse> getCreatedParties(Long memberId) {
        return partyRepository.findCreatedParties(memberId).stream()
                .map(PartySummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PartySummaryResponse> getJoinedParties(Long memberId) {
        return partyRepository.findJoinedParties(memberId, ParticipantStatus.ACCEPTED).stream()
                .map(PartySummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartyDetailResponse getParty(Long partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.PARTY_NOT_FOUND));

        return PartyDetailResponse.from(party);
    }

    @Transactional(readOnly = true)
    public List<PartyParticipantSummaryResponse> getPartyParticipants(Long partyId) {
        partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.PARTY_NOT_FOUND));

        // TODO: 참여자 목록 정책 확정 필요. 현재는 모든 상태(PENDING/ACCEPTED/REJECTED/CANCELLED)를 반환한다.
        return participantRepository.findAllByPartyIdOrderByIdAsc(partyId).stream()
                .map(PartyParticipantSummaryResponse::from)
                .toList();
    }

    @Transactional
    public PartyJoinResponse joinParty(Long partyId, Long memberId, PartyJoinRequest request) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.PARTY_NOT_FOUND));

        if (party.getStatus() != com.wanderpool.be.domain.PartyStatus.RECRUITING) {
            throw new PartyException(PartyErrorCode.PARTY_NOT_RECRUITING);
        }

        if (party.isDriver(memberId)) {
            throw new PartyException(PartyErrorCode.CANNOT_JOIN_OWN_PARTY);
        }

        if (participantRepository.existsByPartyAndMemberId(party, memberId)) {
            throw new PartyException(PartyErrorCode.ALREADY_JOINED);
        }

        debitPointsForJoinIfRequired(partyId, memberId, request.pointCost());

        PartyParticipant participant = PartyParticipant.create(
                party,
                memberId,
                request.pickupName(),
                request.pickupLat(),
                request.pickupLng(),
                request.dropoffName(),
                request.dropoffLat(),
                request.dropoffLng(),
                request.pointCost(),
                request.pointCost() != null && request.pointCost() > 0
        );

        participantRepository.save(participant);
        return PartyJoinResponse.from(participant);
    }

    @Transactional
    public PartyJoinResponse acceptJoinRequest(Long partyId, Long participantId, Long driverMemberId) {
        PartyParticipant participant = loadParticipant(partyId, participantId);
        verifyDriver(participant.getParty(), driverMemberId);

        Party party = participant.getParty();
        if (party.getStatus() != PartyStatus.RECRUITING) {
            throw new PartyException(PartyErrorCode.PARTY_NOT_RECRUITING);
        }

        participant.accept();
        party.incrementPassengers();

        return PartyJoinResponse.from(participant);
    }

    @Transactional
    public PartyJoinResponse rejectJoinRequest(Long partyId, Long participantId, Long driverMemberId, PartyRejectRequest request) {
        PartyParticipant participant = loadParticipant(partyId, participantId);
        verifyDriver(participant.getParty(), driverMemberId);

        String reason = request == null ? null : request.reason();
        participant.reject(reason);
        createRefundOutboxForRejectedParticipantIfRequired(participant);

        return PartyJoinResponse.from(participant);
    }

    @Transactional
    public UpdatePartyStatusResponse startParty(Long partyId, Long memberId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.PARTY_NOT_FOUND));
        verifyDriver(party, memberId);
        party.changeStatus(PartyStatus.IN_PROGRESS);
        return new UpdatePartyStatusResponse(party.getId(), party.getStatus());
    }

    @Transactional
    public UpdatePartyStatusResponse completeParty(Long partyId, Long memberId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.PARTY_NOT_FOUND));
        verifyDriver(party, memberId);
        party.changeStatus(PartyStatus.COMPLETED);
        createCreditOutboxIfRequired(party);
        return new UpdatePartyStatusResponse(party.getId(), party.getStatus());
    }

    @Transactional
    public CancelParticipationResponse cancelParticipation(
            Long partyId, Long participantId, Long memberId, String cancelReason) {

        PartyParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.NOT_PARTICIPANT));

        Party party = participant.getParty();
        if (!partyId.equals(party.getId())) {
            throw new PartyException(PartyErrorCode.NOT_PARTICIPANT);
        }
        if (!participant.getMemberId().equals(memberId)) {
            throw new PartyException(PartyErrorCode.FORBIDDEN);
        }
        if (participant.getStatus() == ParticipantStatus.CANCELLED) {
            throw new PartyException(PartyErrorCode.ALREADY_CANCELLED);
        }
        if (participant.getStatus() == ParticipantStatus.REJECTED) {
            throw new PartyException(PartyErrorCode.CANNOT_CANCEL);
        }
        if (party.getStatus() == PartyStatus.IN_PROGRESS || party.getStatus() == PartyStatus.COMPLETED) {
            throw new PartyException(PartyErrorCode.CANNOT_CANCEL);
        }

        ParticipantStatus statusBeforeCancel = participant.getStatus();
        int refundPoints = participant.isPaymentDebited() && participant.getPointCost() != null
                ? participant.getPointCost()
                : 0;
        participant.cancel(cancelReason);
        if (statusBeforeCancel == ParticipantStatus.ACCEPTED) {
            party.decrementPassengers();
        }

        if (refundPoints > 0) {
            PointRefundCommand command = PointRefundCommand.cancelParticipation(
                    memberId, refundPoints, party.getId(), participantId);
            createRefundOutboxIfAbsent(command);
        }

        return new CancelParticipationResponse(
                participant.getId(),
                party.getId(),
                refundPoints,
                party.getCurrentPassengers(),
                party.getStatus(),
                participant.getCancelledAt()
        );
    }

    private PartyParticipant loadParticipant(Long partyId, Long participantId) {
        PartyParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.NOT_PARTICIPANT));

        if (!participant.getParty().getId().equals(partyId)) {
            throw new PartyException(PartyErrorCode.NOT_PARTICIPANT);
        }
        return participant;
    }

    private void verifyDriver(Party party, Long driverMemberId) {
        if (!party.isDriver(driverMemberId)) {
            throw new PartyException(PartyErrorCode.NOT_PARTY_DRIVER);
        }
    }

    private void debitPointsForJoinIfRequired(Long partyId, Long memberId, Integer pointCost) {
        if (pointCost == null || pointCost <= 0) {
            return;
        }

        try {
            memberClient.debitPoints(PointDebitCommand.joinParty(memberId, pointCost, partyId));
        } catch (InsufficientPointsMemberClientException e) {
            throw new PartyException(PartyErrorCode.INSUFFICIENT_POINTS);
        } catch (RetryableMemberClientException | NonRetryableMemberClientException e) {
            throw new PartyException(PartyErrorCode.MEMBER_SERVICE_ERROR);
        }
    }

    private void createCreditOutboxIfRequired(Party party) {
        int totalPointCost = participantRepository.findAllByPartyIdAndStatusOrderByIdAsc(
                        party.getId(),
                        ParticipantStatus.ACCEPTED
                ).stream()
                .filter(PartyParticipant::isPaymentDebited)
                .map(PartyParticipant::getPointCost)
                .filter(pointCost -> pointCost != null && pointCost > 0)
                .mapToInt(Integer::intValue)
                .sum();

        if (totalPointCost <= 0) {
            return;
        }

        PointCreditCommand command = PointCreditCommand.completeParty(
                party.getDriverMemberId(),
                totalPointCost,
                party.getId()
        );
        createCreditOutboxIfAbsent(command);
    }

    private void createRefundOutboxIfAbsent(PointRefundCommand command) {
        if (pointRefundOutboxRepository.findByRequestId(command.requestId()).isPresent()) {
            return;
        }
        try {
            pointRefundOutboxRepository.saveAndFlush(PointRefundOutbox.from(command));
        } catch (DataIntegrityViolationException e) {
            pointRefundOutboxRepository.findByRequestId(command.requestId())
                    .orElseThrow(() -> e);
        }
    }

    private void createRefundOutboxForRejectedParticipantIfRequired(PartyParticipant participant) {
        Integer pointCost = participant.getPointCost();
        if (!participant.isPaymentDebited() || pointCost == null || pointCost <= 0) {
            return;
        }

        PointRefundCommand command = PointRefundCommand.rejectParticipation(
                participant.getMemberId(),
                pointCost,
                participant.getParty().getId(),
                participant.getId()
        );
        createRefundOutboxIfAbsent(command);
    }

    private void createCreditOutboxIfAbsent(PointCreditCommand command) {
        if (pointCreditOutboxRepository.findByRequestId(command.requestId()).isPresent()) {
            return;
        }
        try {
            pointCreditOutboxRepository.saveAndFlush(PointCreditOutbox.from(command));
        } catch (DataIntegrityViolationException e) {
            pointCreditOutboxRepository.findByRequestId(command.requestId())
                    .orElseThrow(() -> e);
        }
    }
}
