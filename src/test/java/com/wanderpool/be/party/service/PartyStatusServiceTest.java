package com.wanderpool.be.party.service;

import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.PartyParticipant;
import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.credit.PointCreditOutbox;
import com.wanderpool.be.party.credit.PointCreditOutboxRepository;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.refund.PointRefundOutboxRepository;
import com.wanderpool.be.party.repository.PartyParticipantRepository;
import com.wanderpool.be.party.repository.PartyRepository;
import com.wanderpool.be.party.service.dto.UpdatePartyStatusResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PartyStatusServiceTest {

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

    private final LocalDateTime departure = LocalDateTime.now().plusHours(1);
    private final LocalDateTime arrival = departure.plusHours(2);

    private Party createParty(Long driverMemberId) {
        Party party = Party.create(
                driverMemberId,
                "강남 → 판교", "출퇴근 카풀",
                "강남역", 37.4979, 127.0276,
                "판교역", 37.3949, 127.1112,
                departure, arrival, 3
        );
        ReflectionTestUtils.setField(party, "id", 1L);
        return party;
    }

    @Test
    @DisplayName("RECRUITING 상태 파티를 IN_PROGRESS로 전환할 수 있다")
    void startParty_fromRecruiting_success() {
        Party party = createParty(1L);
        given(partyRepository.findById(1L)).willReturn(Optional.of(party));

        UpdatePartyStatusResponse response = partyService.startParty(1L, 1L);

        assertThat(response.status()).isEqualTo(PartyStatus.IN_PROGRESS);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Driver가 아니면 운행 시작 시 NOT_PARTY_DRIVER 예외가 발생한다")
    void startParty_notDriver_throwsException() {
        Party party = createParty(1L);
        given(partyRepository.findById(1L)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> partyService.startParty(1L, 99L))
                .isInstanceOf(PartyException.class)
                .hasMessage(PartyErrorCode.NOT_PARTY_DRIVER.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 파티 운행 시작 시 PARTY_NOT_FOUND 예외가 발생한다")
    void startParty_partyNotFound_throwsException() {
        given(partyRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> partyService.startParty(999L, 1L))
                .isInstanceOf(PartyException.class)
                .hasMessage(PartyErrorCode.PARTY_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("COMPLETED 상태에서 IN_PROGRESS 전환 시 INVALID_STATUS_TRANSITION 예외가 발생한다")
    void startParty_invalidTransition_throwsException() {
        Party party = createParty(1L);
        party.changeStatus(PartyStatus.IN_PROGRESS);
        party.changeStatus(PartyStatus.COMPLETED);
        given(partyRepository.findById(1L)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> partyService.startParty(1L, 1L))
                .isInstanceOf(PartyException.class)
                .hasMessage(PartyErrorCode.INVALID_STATUS_TRANSITION.getMessage());
    }

    @Test
    @DisplayName("IN_PROGRESS 상태 파티를 COMPLETED로 전환하고 적립 outbox를 생성할 수 있다")
    void completeParty_success() {
        Party party = createParty(1L);
        party.changeStatus(PartyStatus.IN_PROGRESS);
        PartyParticipant accepted = PartyParticipant.create(
                party, 2L, "강남역", 37.4, 127.0, "판교역", 1000
        );
        accepted.accept();
        given(partyRepository.findById(1L)).willReturn(Optional.of(party));
        given(participantRepository.findAllByPartyIdAndStatusOrderByIdAsc(1L, com.wanderpool.be.domain.ParticipantStatus.ACCEPTED))
                .willReturn(List.of(accepted));
        given(pointCreditOutboxRepository.findByRequestId("party-complete:1:driver-credit")).willReturn(Optional.empty());
        given(pointCreditOutboxRepository.saveAndFlush(any(PointCreditOutbox.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UpdatePartyStatusResponse response = partyService.completeParty(1L, 1L);

        assertThat(response.status()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        verify(pointCreditOutboxRepository).saveAndFlush(any(PointCreditOutbox.class));
    }

    @Test
    @DisplayName("Driver가 아니면 운행 종료 시 NOT_PARTY_DRIVER 예외가 발생한다")
    void completeParty_notDriver_throwsException() {
        Party party = createParty(1L);
        party.changeStatus(PartyStatus.IN_PROGRESS);
        given(partyRepository.findById(1L)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> partyService.completeParty(1L, 99L))
                .isInstanceOf(PartyException.class)
                .hasMessage(PartyErrorCode.NOT_PARTY_DRIVER.getMessage());
    }

    @Test
    @DisplayName("RECRUITING 상태에서 COMPLETED 전환 시 INVALID_STATUS_TRANSITION 예외가 발생한다")
    void completeParty_invalidTransition_throwsException() {
        Party party = createParty(1L);
        given(partyRepository.findById(1L)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> partyService.completeParty(1L, 1L))
                .isInstanceOf(PartyException.class)
                .hasMessage(PartyErrorCode.INVALID_STATUS_TRANSITION.getMessage());
    }
}
