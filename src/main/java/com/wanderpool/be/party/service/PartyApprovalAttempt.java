package com.wanderpool.be.party.service;

import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.PartyParticipant;
import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.common.apiResponse.exception.PartyNotFoundException;
import com.wanderpool.be.party.repository.PartyParticipantRepository;
import com.wanderpool.be.party.repository.PartyRepository;
import com.wanderpool.be.party.service.dto.PartyJoinResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * acceptJoinRequest()의 실제 승낙 시도 1회분을 담당한다.
 *
 * <p>PartyService에서 self-invocation으로 호출하면 프록시를 거치지 않아 @Transactional이
 * 적용되지 않으므로, 재시도마다 새 트랜잭션(REQUIRES_NEW)을 강제하기 위해 별도 빈으로 분리했다.
 * 재시도가 실패한 트랜잭션의 stale한 영속성 컨텍스트를 재사용하지 않고, 매 시도마다 최신
 * 데이터를 다시 읽어오도록 하는 것이 핵심이다.
 */
@Component
@RequiredArgsConstructor
class PartyApprovalAttempt {

    private final PartyRepository partyRepository;
    private final PartyParticipantRepository participantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PartyJoinResponse attempt(Long partyId, Long participantId, Long driverMemberId, String lockStrategy) {
        PartyParticipant participant = loadParticipant(partyId, participantId);

        // 전략 분기: 비관적이면 락을 걸고 재조회, 낙관적이면 기존 참조 사용.
        // participant.getParty()를 먼저 건드리면(예: verifyDriver) 그 시점에 락 없이
        // Party가 영속성 컨텍스트에 로드되어버려서, 뒤이은 findByIdForUpdate가 실제로는
        // DB 락만 걸고 이미 캐시된(stale) 엔티티를 그대로 반환한다 — 그러면 락을 걸어도
        // 갱신된 상태를 못 보고 그대로 버전 충돌이 난다. 그래서 락 조회를 다른 Party 접근보다 먼저 해야 한다.
        Party party = "PESSIMISTIC".equals(lockStrategy)
                ? partyRepository.findByIdForUpdate(partyId)
                        .orElseThrow(PartyNotFoundException::new)
                : participant.getParty();
        verifyDriver(party, driverMemberId);

        if (party.getStatus() != PartyStatus.RECRUITING) {
            throw new PartyException(PartyErrorCode.PARTY_NOT_RECRUITING);
        }

        participant.accept();
        party.incrementPassengers();

        return PartyJoinResponse.from(participant);
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
}
