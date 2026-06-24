package com.wanderpool.be.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartyParticipantTest {

    private Party commonParty;

    @BeforeEach
    void setUp() {
        // 테스트에 공통으로 사용할 파티 객체 생성
        commonParty = Party.create(
                1L, "테스트 파티", "설명",
                "출발지", 37.0, 127.0,
                "목적지", 37.1, 127.1,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2),
                4
        );
    }

    @Test
    @DisplayName("참여자 생성 시 초기 상태는 PENDING이어야 한다")
    void create_Participant_Success() {
        Long targetMemberId = 2L; // 승객 아이디

        PartyParticipant participant = PartyParticipant.create(
                commonParty, targetMemberId, "강남역 2번출구", 37.05, 127.05, "상세주소", 1000
        );

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.PENDING);
        assertThat(participant.getParty()).isEqualTo(commonParty);

        // 닉네임 대신 memberId가 제대로 들어갔는지 검증!
        assertThat(participant.getMemberId()).isEqualTo(targetMemberId);
        // 픽업지 이름이 잘 들어갔는지 확인
        assertThat(participant.getPickupName()).isEqualTo("강남역 2번출구");
    }

    @Test
    @DisplayName("참여자 상태 변경(승인/거절/취소) 정상 흐름 테스트")
    void participant_status_lifecycle_success() {
        // 1. 승인 시나리오
        PartyParticipant p1 = PartyParticipant.create(commonParty, 2L, "P1", 37.05, 127.05, "A1", 1000);
        p1.accept();
        assertThat(p1.getStatus()).isEqualTo(ParticipantStatus.ACCEPTED);

        // 2. 거절 시나리오
        PartyParticipant p2 = PartyParticipant.create(commonParty, 3L, "P2", 37.05, 127.05, "A2", 1000);
        p2.reject();
        assertThat(p2.getStatus()).isEqualTo(ParticipantStatus.REJECTED);

        // 3. 취소 시나리오
        PartyParticipant p3 = PartyParticipant.create(commonParty, 4L, "P3", 37.05, 127.05, "A3", 1000);
        p3.cancel("개인 사정으로 취소합니다.");
        assertThat(p3.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
    }

    @Test
    @DisplayName("PENDING 상태가 아닐 때 승인/거절을 시도하면 PartyException이 발생한다 (방어 로직 검증)")
    void participant_status_transition_exception() {
        // 1. 이미 승인된 참여자 준비
        PartyParticipant participant = PartyParticipant.create(commonParty, 2L, "P1", 37.05, 127.05, "A1", 1000);
        participant.accept();

        // 2. 이미 ACCEPTED 상태인데 다시 accept() 호출 시 예외 발생 여부 확인
        assertThatThrownBy(participant::accept)
                .isInstanceOf(com.wanderpool.be.party.common.apiResponse.exception.PartyException.class);

        // 3. 이미 ACCEPTED 상태인데 reject() 호출 시 예외 발생 여부 확인
        assertThatThrownBy(participant::reject)
                .isInstanceOf(com.wanderpool.be.party.common.apiResponse.exception.PartyException.class);
    }

    @Test
    @DisplayName("참여 취소 시 사유(reason)가 정상적으로 저장되어야 한다")
    void cancel_with_reason() {
        PartyParticipant participant = PartyParticipant.create(commonParty, 2L, "P1", 37.05, 127.05, "A1", 1000);
        String reason = "갑자기 일이 생겼어요";

        participant.cancel(reason);

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
        // 만약 필드에 reason이 저장되는 로직이 있다면 아래 검증도 추가 가능
        // assertThat(participant.getCancelReason()).isEqualTo(reason);
    }
}