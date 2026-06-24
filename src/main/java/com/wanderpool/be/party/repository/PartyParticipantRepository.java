package com.wanderpool.be.party.repository;

import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.PartyParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyParticipantRepository extends JpaRepository<PartyParticipant, Long> {
    boolean existsByPartyAndMemberId(Party party, Long memberId);

    List<PartyParticipant> findAllByPartyIdOrderByIdAsc(Long partyId);

    List<PartyParticipant> findAllByPartyIdAndStatusOrderByIdAsc(Long partyId, ParticipantStatus status);
}
