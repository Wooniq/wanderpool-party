package com.wanderpool.be.party.service;

import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.PartyParticipant;
import com.wanderpool.be.party.client.InsufficientPointsMemberClientException;
import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.credit.PointCreditOutboxRepository;
import com.wanderpool.be.party.refund.PointRefundOutboxRepository;
import com.wanderpool.be.party.repository.PartyParticipantRepository;
import com.wanderpool.be.party.repository.PartyRepository;
import com.wanderpool.be.party.service.dto.PartyJoinRequest;
import com.wanderpool.be.party.service.dto.PartyJoinResponse;
import com.wanderpool.be.party.service.dto.PartyParticipantSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PartyJoinServiceTest {

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PartyParticipantRepository participantRepository;

    @Mock
    private PointRefundOutboxRepository pointRefundOutboxRepository;

    @Mock
    private PointCreditOutboxRepository pointCreditOutboxRepository;

    @Mock
    private MemberClient memberClient;

    @InjectMocks
    private PartyService partyService;

    private final Long driverId = 1L;
    private final Long passengerId = 2L;
    private final Long partyId = 100L;

    private Party createParty(int max) {
        Party party = Party.create(
                driverId, "title", "desc",
                "출발지", 37.0, 127.0,
                "목적지", 37.1, 127.1,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2),
                max
        );
        try {
            Field idField = Party.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(party, partyId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return party;
    }

    private PartyJoinRequest validRequest() {
        return new PartyJoinRequest("서울역", 37.5, 127.0, "강남역", 300);
    }

    private void setId(Object target, String fieldName, Long value) {
        try {
            Field idField = target.getClass().getDeclaredField(fieldName);
            idField.setAccessible(true);
            idField.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("참여 요청이 성공하면 포인트 차감 후 PENDING 상태로 저장되고 인원은 증가하지 않는다")
    void joinParty_success() {
        Party party = createParty(4);
        given(partyRepository.findById(partyId)).willReturn(Optional.of(party));
        given(participantRepository.existsByPartyAndMemberId(party, passengerId)).willReturn(false);

        PartyJoinResponse response = partyService.joinParty(partyId, passengerId, validRequest());

        assertThat(response.status()).isEqualTo(ParticipantStatus.PENDING);
        assertThat(response.partyId()).isEqualTo(partyId);
        assertThat(response.pointCost()).isEqualTo(300);
        assertThat(response.currentPassengers()).isEqualTo(0);
        assertThat(response.maxPassengers()).isEqualTo(4);
        verify(participantRepository).save(any(PartyParticipant.class));
        verify(memberClient).debitPoints(any());
    }

    @Test
    @DisplayName("포인트가 부족하면 참여 요청은 실패하고 참여 정보는 저장되지 않는다")
    void joinParty_insufficientPoints() {
        Party party = createParty(4);
        given(partyRepository.findById(partyId)).willReturn(Optional.of(party));
        given(participantRepository.existsByPartyAndMemberId(party, passengerId)).willReturn(false);
        org.mockito.Mockito.doThrow(new InsufficientPointsMemberClientException("insufficient points", null))
                .when(memberClient).debitPoints(any());

        assertThatThrownBy(() -> partyService.joinParty(partyId, passengerId, validRequest()))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(pointRefundOutboxRepository, pointCreditOutboxRepository);
    }

    @Test
    @DisplayName("존재하지 않는 파티는 PARTY_NOT_FOUND를 던진다")
    void joinParty_notFound() {
        given(partyRepository.findById(partyId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> partyService.joinParty(partyId, passengerId, validRequest()))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(participantRepository);
    }

    @Test
    @DisplayName("본인 파티에 참여 시도 시 CANNOT_JOIN_OWN_PARTY를 던진다")
    void joinParty_ownParty() {
        Party party = createParty(4);
        given(partyRepository.findById(partyId)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> partyService.joinParty(partyId, driverId, validRequest()))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(participantRepository);
    }

    @Test
    @DisplayName("이미 참여 중이면 ALREADY_JOINED를 던진다")
    void joinParty_alreadyJoined() {
        Party party = createParty(4);
        given(partyRepository.findById(partyId)).willReturn(Optional.of(party));
        given(participantRepository.existsByPartyAndMemberId(party, passengerId)).willReturn(true);

        assertThatThrownBy(() -> partyService.joinParty(partyId, passengerId, validRequest()))
                .isInstanceOf(PartyException.class);
    }

    @Test
    @DisplayName("파티가 모집 중이 아니면 PARTY_NOT_RECRUITING을 던진다")
    void joinParty_notRecruiting() {
        Party party = createParty(1);
        party.incrementPassengers(); // 1명 진입 → CLOSED

        given(partyRepository.findById(partyId)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> partyService.joinParty(partyId, passengerId, validRequest()))
                .isInstanceOf(PartyException.class);
    }

    // ───────────── accept/reject ─────────────

    private PartyParticipant buildPendingParticipant(Party party) {
        PartyParticipant participant = PartyParticipant.create(
                party, passengerId, "P", 37.05, 127.05, "D", 300
        );
        setId(participant, "id", 500L);
        return participant;
    }

    @Test
    @DisplayName("파티 참여자 목록 조회 시 응답으로 변환된다")
    void getPartyParticipants_success() {
        Party party = createParty(4);
        PartyParticipant first = PartyParticipant.create(
                party, 2L, "서울역", 37.5, 127.0, "강남역", 300
        );
        PartyParticipant second = PartyParticipant.create(
                party, 3L, "사당역", 37.4, 127.0, "판교역", 500
        );
        setId(first, "id", 10L);
        setId(second, "id", 11L);
        second.accept();

        given(partyRepository.findById(partyId)).willReturn(Optional.of(party));
        given(participantRepository.findAllByPartyIdOrderByIdAsc(partyId)).willReturn(List.of(first, second));

        List<PartyParticipantSummaryResponse> response = partyService.getPartyParticipants(partyId);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).participantId()).isEqualTo(10L);
        assertThat(response.get(0).pickupName()).isEqualTo("서울역");
        assertThat(response.get(0).status()).isEqualTo(ParticipantStatus.PENDING);
        assertThat(response.get(1).participantId()).isEqualTo(11L);
        assertThat(response.get(1).pickupName()).isEqualTo("사당역");
        assertThat(response.get(1).status()).isEqualTo(ParticipantStatus.ACCEPTED);
    }

    @Test
    @DisplayName("참여자가 없는 파티의 목록 조회 시 빈 리스트를 반환한다")
    void getPartyParticipants_empty_returnsEmptyList() {
        Party party = createParty(4);
        given(partyRepository.findById(partyId)).willReturn(Optional.of(party));
        given(participantRepository.findAllByPartyIdOrderByIdAsc(partyId)).willReturn(List.of());

        List<PartyParticipantSummaryResponse> response = partyService.getPartyParticipants(partyId);

        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 파티의 참여자 목록 조회 시 PARTY_NOT_FOUND를 던진다")
    void getPartyParticipants_notFound() {
        given(partyRepository.findById(partyId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> partyService.getPartyParticipants(partyId))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(participantRepository);
    }

    @Test
    @DisplayName("Driver가 PENDING 참여 요청을 승낙하면 ACCEPTED로 바뀌고 인원이 증가한다")
    void acceptJoinRequest_success() {
        Party party = createParty(4);
        PartyParticipant participant = buildPendingParticipant(party);
        given(participantRepository.findById(500L)).willReturn(Optional.of(participant));

        PartyJoinResponse response = partyService.acceptJoinRequest(partyId, 500L, driverId);

        assertThat(response.status()).isEqualTo(ParticipantStatus.ACCEPTED);
        assertThat(response.currentPassengers()).isEqualTo(1);
    }

    @Test
    @DisplayName("Driver가 아닌 사용자가 승낙을 시도하면 NOT_PARTY_DRIVER를 던진다")
    void acceptJoinRequest_notDriver() {
        Party party = createParty(4);
        PartyParticipant participant = buildPendingParticipant(party);
        given(participantRepository.findById(500L)).willReturn(Optional.of(participant));

        assertThatThrownBy(() -> partyService.acceptJoinRequest(partyId, 500L, 999L))
                .isInstanceOf(PartyException.class);
    }

    @Test
    @DisplayName("존재하지 않는 참여 요청을 승낙하면 NOT_PARTICIPANT를 던진다")
    void acceptJoinRequest_notFound() {
        given(participantRepository.findById(500L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> partyService.acceptJoinRequest(partyId, 500L, driverId))
                .isInstanceOf(PartyException.class);
    }

    @Test
    @DisplayName("PATH의 partyId와 참여 요청의 partyId가 다르면 NOT_PARTICIPANT를 던진다")
    void acceptJoinRequest_partyMismatch() {
        Party party = createParty(4);
        PartyParticipant participant = buildPendingParticipant(party);
        given(participantRepository.findById(500L)).willReturn(Optional.of(participant));

        assertThatThrownBy(() -> partyService.acceptJoinRequest(999L, 500L, driverId))
                .isInstanceOf(PartyException.class);
    }

    @Test
    @DisplayName("이미 ACCEPTED된 참여를 다시 승낙하면 PARTICIPANT_NOT_PENDING을 던진다")
    void acceptJoinRequest_notPending() {
        Party party = createParty(4);
        PartyParticipant participant = buildPendingParticipant(party);
        participant.accept();
        party.incrementPassengers();
        given(participantRepository.findById(500L)).willReturn(Optional.of(participant));

        assertThatThrownBy(() -> partyService.acceptJoinRequest(partyId, 500L, driverId))
                .isInstanceOf(PartyException.class);
    }

    @Test
    @DisplayName("Driver가 PENDING 참여 요청을 거절하면 REJECTED로 바뀌고 선차감된 포인트 환불 outbox가 생성된다")
    void rejectJoinRequest_refundsDebitedParticipant() {
        Party party = createParty(4);
        PartyParticipant participant = buildPendingParticipant(party);
        given(participantRepository.findById(500L)).willReturn(Optional.of(participant));
        given(pointRefundOutboxRepository.findByRequestId("party-reject:100:500")).willReturn(Optional.empty());
        given(pointRefundOutboxRepository.saveAndFlush(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        PartyJoinResponse response = partyService.rejectJoinRequest(partyId, 500L, driverId, null);

        assertThat(response.status()).isEqualTo(ParticipantStatus.REJECTED);
        verify(pointRefundOutboxRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("선차감되지 않은 참여 요청을 거절하면 환불 outbox를 만들지 않는다")
    void rejectJoinRequest_doesNotRefundWhenNotDebited() {
        Party party = createParty(4);
        PartyParticipant participant = PartyParticipant.create(
                party, passengerId, "P", 37.05, 127.05, "D", null, null, 300, false
        );
        setId(participant, "id", 500L);
        given(participantRepository.findById(500L)).willReturn(Optional.of(participant));

        PartyJoinResponse response = partyService.rejectJoinRequest(partyId, 500L, driverId, null);

        assertThat(response.status()).isEqualTo(ParticipantStatus.REJECTED);
        verifyNoInteractions(pointRefundOutboxRepository);
    }

    @Test
    @DisplayName("Driver가 PENDING 참여 요청을 거절하면 REJECTED로 바뀐다 (사유 포함)")
    void rejectJoinRequest_success_withReason() {
        Party party = createParty(4);
        PartyParticipant participant = buildPendingParticipant(party);
        given(participantRepository.findById(500L)).willReturn(Optional.of(participant));

        PartyJoinResponse response = partyService.rejectJoinRequest(
                partyId, 500L, driverId,
                new com.wanderpool.be.party.service.dto.PartyRejectRequest("픽업지 경로 이탈")
        );

        assertThat(response.status()).isEqualTo(ParticipantStatus.REJECTED);
        assertThat(response.currentPassengers()).isEqualTo(0);
    }

    @Test
    @DisplayName("거절 요청 body 없이도 정상 동작한다")
    void rejectJoinRequest_success_noBody() {
        Party party = createParty(4);
        PartyParticipant participant = buildPendingParticipant(party);
        given(participantRepository.findById(500L)).willReturn(Optional.of(participant));

        PartyJoinResponse response = partyService.rejectJoinRequest(partyId, 500L, driverId, null);

        assertThat(response.status()).isEqualTo(ParticipantStatus.REJECTED);
    }
}
