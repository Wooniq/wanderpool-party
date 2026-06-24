package com.wanderpool.be.service;

import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.domain.PartyParticipant;
import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.credit.PointCreditOutboxRepository;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.refund.PointRefundOutbox;
import com.wanderpool.be.party.refund.PointRefundOutboxRepository;
import com.wanderpool.be.party.repository.PartyParticipantRepository;
import com.wanderpool.be.party.repository.PartyRepository;
import com.wanderpool.be.party.service.PartyService;
import com.wanderpool.be.party.service.dto.CancelParticipationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CancelParticipationServiceTest {

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

    private Party party;
    private PartyParticipant participant;
    private final Long partyId = 1L;
    private final Long participantId = 10L;
    private final Long userId = 2L;

    @BeforeEach
    void setUp() {
        party = Party.create(
                1L, "강남 → 판교", "설명",
                "강남역", 37.4979, 127.0276,
                "판교역", 37.3949, 127.1112,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3),
                3
        );
        ReflectionTestUtils.setField(party, "id", partyId);
        party.incrementPassengers();

        participant = PartyParticipant.create(party, userId, "강남역 2번출구", 37.05, 127.05, "판교역", 1000);
        participant.accept();
    }

    @Test
    @DisplayName("ACCEPTED 참여자가 취소하면 포인트 환불 및 인원 감소가 처리된다")
    void cancelParticipation_success_accepted() {
        given(participantRepository.findById(participantId)).willReturn(Optional.of(participant));
        given(pointRefundOutboxRepository.findByRequestId("party-cancel:1:10")).willReturn(Optional.empty());
        given(pointRefundOutboxRepository.saveAndFlush(any(PointRefundOutbox.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CancelParticipationResponse response = partyService.cancelParticipation(
                partyId, participantId, userId, "개인 사정");

        assertThat(response.refundedPoints()).isEqualTo(1000);
        assertThat(response.currentPassengers()).isEqualTo(0);
        assertThat(response.partyStatus()).isEqualTo(PartyStatus.RECRUITING);
        assertThat(response.cancelledAt()).isNotNull();
        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
        assertThat(participant.getCancelReason()).isEqualTo("개인 사정");

        verify(pointRefundOutboxRepository).saveAndFlush(any(PointRefundOutbox.class));
    }

    @Test
    @DisplayName("취소 시 파티가 CLOSED였으면 RECRUITING으로 복원된다")
    void cancelParticipation_restoresClosedToRecruiting() {
        Party fullParty = Party.create(
                1L, "풀파티", "설명",
                "강남역", 37.4979, 127.0276,
                "판교역", 37.3949, 127.1112,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3),
                1
        );
        ReflectionTestUtils.setField(fullParty, "id", partyId);
        fullParty.incrementPassengers(); // currentPassengers=1, maxPassengers=1 → CLOSED

        assertThat(fullParty.getStatus()).isEqualTo(PartyStatus.CLOSED);

        PartyParticipant p = PartyParticipant.create(fullParty, userId, "픽업", 37.05, 127.05, "하차", 500);
        p.accept();

        given(participantRepository.findById(participantId)).willReturn(Optional.of(p));
        given(pointRefundOutboxRepository.findByRequestId("party-cancel:1:10")).willReturn(Optional.empty());
        given(pointRefundOutboxRepository.saveAndFlush(any(PointRefundOutbox.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CancelParticipationResponse response = partyService.cancelParticipation(
                partyId, participantId, userId, null);

        assertThat(response.partyStatus()).isEqualTo(PartyStatus.RECRUITING);
        assertThat(response.currentPassengers()).isEqualTo(0);
    }

    @Test
    @DisplayName("존재하지 않는 참여 ID 조회 시 NOT_PARTICIPANT 예외가 발생한다")
    void cancelParticipation_notFound_throwsException() {
        given(participantRepository.findById(participantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> partyService.cancelParticipation(partyId, participantId, userId, null))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(pointRefundOutboxRepository);
    }

    @Test
    @DisplayName("다른 파티의 participantId 요청 시 NOT_PARTICIPANT 예외가 발생한다")
    void cancelParticipation_wrongPartyId_throwsException() {
        given(participantRepository.findById(participantId)).willReturn(Optional.of(participant));

        assertThatThrownBy(() -> partyService.cancelParticipation(999L, participantId, userId, null))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(pointRefundOutboxRepository);
    }

    @Test
    @DisplayName("본인이 아닌 userId로 취소 요청 시 FORBIDDEN 예외가 발생한다")
    void cancelParticipation_wrongUser_throwsException() {
        given(participantRepository.findById(participantId)).willReturn(Optional.of(participant));

        assertThatThrownBy(() -> partyService.cancelParticipation(partyId, participantId, 99L, null))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(pointRefundOutboxRepository);
    }

    @Test
    @DisplayName("이미 취소된 참여를 다시 취소하면 ALREADY_CANCELLED 예외가 발생한다")
    void cancelParticipation_alreadyCancelled_throwsException() {
        participant.cancel("첫 번째 취소");
        given(participantRepository.findById(participantId)).willReturn(Optional.of(participant));

        assertThatThrownBy(() -> partyService.cancelParticipation(partyId, participantId, userId, null))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(pointRefundOutboxRepository);
    }

    @Test
    @DisplayName("진행 중인 파티의 참여를 취소하면 CANNOT_CANCEL 예외가 발생한다")
    void cancelParticipation_inProgress_throwsException() {
        party.changeStatus(PartyStatus.IN_PROGRESS);
        given(participantRepository.findById(participantId)).willReturn(Optional.of(participant));

        assertThatThrownBy(() -> partyService.cancelParticipation(partyId, participantId, userId, null))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(pointRefundOutboxRepository);
    }

    @Test
    @DisplayName("완료된 파티의 참여를 취소하면 CANNOT_CANCEL 예외가 발생한다")
    void cancelParticipation_completed_throwsException() {
        party.changeStatus(PartyStatus.IN_PROGRESS);
        party.changeStatus(PartyStatus.COMPLETED);
        given(participantRepository.findById(participantId)).willReturn(Optional.of(participant));

        assertThatThrownBy(() -> partyService.cancelParticipation(partyId, participantId, userId, null))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(pointRefundOutboxRepository);
    }

    @Test
    @DisplayName("pointCost가 0이면 포인트 환불 호출을 하지 않는다")
    void cancelParticipation_zeroPointCost_noRefund() {
        PartyParticipant zeroPointParticipant = PartyParticipant.create(
                party, userId, "픽업", 37.05, 127.05, "하차", 0);
        zeroPointParticipant.accept();

        given(participantRepository.findById(participantId)).willReturn(Optional.of(zeroPointParticipant));

        CancelParticipationResponse response = partyService.cancelParticipation(
                partyId, participantId, userId, null);

        assertThat(response.refundedPoints()).isEqualTo(0);
        verifyNoInteractions(pointRefundOutboxRepository);
    }

    @Test
    @DisplayName("실제 선차감되지 않은 참여는 취소되어도 환불 outbox를 만들지 않는다")
    void cancelParticipation_notDebited_noRefund() {
        PartyParticipant notDebitedParticipant = PartyParticipant.create(
                party, userId, "픽업", 37.05, 127.05, "하차", null, null, 1000, false);
        notDebitedParticipant.accept();
        given(participantRepository.findById(participantId)).willReturn(Optional.of(notDebitedParticipant));

        CancelParticipationResponse response = partyService.cancelParticipation(
                partyId, participantId, userId, null);

        assertThat(response.refundedPoints()).isEqualTo(0);
        verifyNoInteractions(pointRefundOutboxRepository);
    }

    @Test
    @DisplayName("PENDING 참여자가 취소하면 환불 outbox는 만들지만 현재 탑승 인원은 감소하지 않는다")
    void cancelParticipation_pendingParticipant_doesNotDecrementPassengers() {
        PartyParticipant pendingParticipant = PartyParticipant.create(
                party, userId, "픽업", 37.05, 127.05, "하차", 1000);
        given(participantRepository.findById(participantId)).willReturn(Optional.of(pendingParticipant));
        given(pointRefundOutboxRepository.findByRequestId("party-cancel:1:10")).willReturn(Optional.empty());
        given(pointRefundOutboxRepository.saveAndFlush(any(PointRefundOutbox.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CancelParticipationResponse response = partyService.cancelParticipation(
                partyId, participantId, userId, "대기 취소");

        assertThat(response.refundedPoints()).isEqualTo(1000);
        assertThat(response.currentPassengers()).isEqualTo(1);
        assertThat(pendingParticipant.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
        verify(pointRefundOutboxRepository).saveAndFlush(any(PointRefundOutbox.class));
    }

    @Test
    @DisplayName("동일 requestId 동시 저장 충돌은 기존 outbox를 멱등 처리로 간주한다")
    void cancelParticipation_duplicateOutboxSave_treatsAsIdempotent() {
        PointRefundOutbox existing = PointRefundOutbox.from(
                com.wanderpool.be.party.client.PointRefundCommand.cancelParticipation(userId, 1000, partyId, participantId)
        );
        given(participantRepository.findById(participantId)).willReturn(Optional.of(participant));
        given(pointRefundOutboxRepository.findByRequestId("party-cancel:1:10"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(existing));
        given(pointRefundOutboxRepository.saveAndFlush(any(PointRefundOutbox.class)))
                .willThrow(new DataIntegrityViolationException("duplicate request_id"));

        CancelParticipationResponse response = partyService.cancelParticipation(
                partyId, participantId, userId, "개인 사정");

        assertThat(response.refundedPoints()).isEqualTo(1000);
        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
        verify(pointRefundOutboxRepository).saveAndFlush(any(PointRefundOutbox.class));
    }
}
