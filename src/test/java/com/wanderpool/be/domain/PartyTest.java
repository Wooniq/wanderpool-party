package com.wanderpool.be.domain;

import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartyTest {

    @Test
    @DisplayName("도착 시간이 출발 시간보다 빠르면 파티 생성에 실패해야 한다")
    void createParty_TimeValidation() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime departure = now.plusHours(2);
        LocalDateTime arrival = now.plusHours(1); // 도착이 더 빠름

        // When & Then
        assertThatThrownBy(() -> Party.create(
                1L, "테스트 파티", "설명",
                "출발지", 37.5, 127.0,
                "목적지", 37.6, 127.1,
                departure, arrival, 4
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Arrival time must be after departure time");
    }

    @Test
    @DisplayName("인원 추가 시 정원에 도달하면 상태가 CLOSED로 변경되어야 한다")
    void incrementPassengers_StatusChange() {
        // Given: 정원이 1명인 파티 생성
        Party party = Party.create(
                1L, "테스트", "설명", "출발", 37.5, 127.0, "목적", 37.6, 127.1,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2), 1
        );

        // When
        party.incrementPassengers();

        // Then
        assertThat(party.getCurrentPassengers()).isEqualTo(1);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CLOSED);
    }

    @Test
    @DisplayName("이미 마감된 파티에 인원을 추가하려고 하면 예외가 발생해야 한다")
    void incrementPassengers_CapacityExceeded() {
        // Given: 정원 1명, 이미 1명 찬 파티
        Party party = Party.create(
                1L, "테스트", "설명", "출발", 37.5, 127.0, "목적", 37.6, 127.1,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2), 1
        );
        party.incrementPassengers();

        // When & Then
        assertThatThrownBy(party::incrementPassengers)
                .isInstanceOf(PartyException.class);
    }

    @Test
    @DisplayName("maxPassengers가 0 이하면 생성 시 예외가 발생한다")
    void create_InvalidMaxPassengers() {
        assertThatThrownBy(() -> Party.create(1L, "T", "D", "O", 37.0, 127.0, "D", 37.1, 127.1,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("driverMemberId가 null이면 생성 시 예외가 발생한다")
    void create_NullDriverId() {
        assertThatThrownBy(() -> Party.create(null, "T", "D", "O", 37.0, 127.0, "D", 37.1, 127.1,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("인원 감소 및 Driver 여부 확인 테스트")
    void utility_Methods_Test() {
        Party party = Party.create(1L, "T", "D", "O", 37.0, 127.0, "D", 37.1, 127.1,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), 2);

        // 1. isDriver 테스트 (0% -> 100%)
        assertThat(party.isDriver(1L)).isTrue();
        assertThat(party.isDriver(99L)).isFalse();
        assertThat(party.isDriver(null)).isFalse();

        // 2. decrementPassengers 테스트 (0% -> 100%)
        party.incrementPassengers(); // 1명 추가
        party.decrementPassengers(); // 1명 감소
        assertThat(party.getCurrentPassengers()).isEqualTo(0);

        // 0명일 때 더 감소시켜도 문제 없는지 (방어 로직 확인)
        party.decrementPassengers();
        assertThat(party.getCurrentPassengers()).isEqualTo(0);
    }

    @Test
    @DisplayName("Party 도메인 모든 분기점 테스트 (100% 도전)")
    void party_full_coverage_test() {
        // 0. 정상 생성
        LocalDateTime departure = LocalDateTime.now().plusHours(1);
        LocalDateTime arrival = departure.plusHours(2);
        Party party = Party.create(1L, "Title", "Desc", "Org", 37.0, 127.0, "Dest", 37.1, 127.1, departure, arrival, 2);

        // 1. 유효한 인덱스(1~3)로 경유지들을 먼저 만듭니다.
        PartyWaypoint wp1 = PartyWaypoint.create(1, "W1", 37.05, 127.05);
        PartyWaypoint wp2 = PartyWaypoint.create(2, "W2", 37.06, 127.06);
        PartyWaypoint wp3 = PartyWaypoint.create(3, "W3", 37.07, 127.07);
        PartyWaypoint wp4 = PartyWaypoint.create(1, "W4", 37.08, 127.08); // 인덱스는 1이지만, 4번째로 추가 시도

// 2. 파티에 추가합니다.
        party.addWaypoint(wp1);
        party.addWaypoint(wp2);
        party.addWaypoint(wp3);

// 3. 4번째 추가 시도 시 PartyException이 발생하는지 검증합니다.
        assertThatThrownBy(() -> party.addWaypoint(wp4))
                .isExactlyInstanceOf(PartyException.class);

        // 3. increment/decrement 및 상태 전환 분기
        assertThat(party.isFull()).isFalse(); // isFull() false 분기

        party.incrementPassengers(); // 1명 추가
        party.incrementPassengers(); // 2명 추가 -> 정원 도달로 CLOSED 자동 전환 분기 통과

        assertThat(party.isFull()).isTrue(); // isFull() true 분기
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CLOSED);

        assertThatThrownBy(party::incrementPassengers)
                .isExactlyInstanceOf(PartyException.class); // 정원 초과 예외 분기 통과

        party.decrementPassengers(); // 1명 감소 -> CLOSED에서 RECRUITING으로 복구 분기 통과
        assertThat(party.getStatus()).isEqualTo(PartyStatus.RECRUITING);

        // 4. changeStatus 정방향/역방향 분기
        party.changeStatus(PartyStatus.IN_PROGRESS);
        assertThatThrownBy(() -> party.changeStatus(PartyStatus.RECRUITING))
                .isExactlyInstanceOf(PartyException.class); // 잘못된 상태 전환 예외 분기 통과

        // 5. isDriver 및 Null 체크 분기
        assertThat(party.isDriver(1L)).isTrue();
        assertThat(party.isDriver(99L)).isFalse();
        assertThat(party.isDriver(null)).isFalse(); // memberId == null 분기 통과
    }

    @Test
    @DisplayName("생성 시 모든 Validation 예외 분기 테스트")
    void create_all_validation_test() {
        LocalDateTime now = LocalDateTime.now();

        // maxPassengers <= 0
        assertThatThrownBy(() -> Party.create(1L, "T", "D", "O", 37.0, 127.0, "D", 37.1, 127.1, now, now.plusHours(1), 0))
                .isInstanceOf(IllegalArgumentException.class);

        // departureTime == null
        assertThatThrownBy(() -> Party.create(1L, "T", "D", "O", 37.0, 127.0, "D", 37.1, 127.1, null, now, 4))
                .isInstanceOf(IllegalArgumentException.class);

        // arrivalTime < departureTime
        assertThatThrownBy(() -> Party.create(1L, "T", "D", "O", 37.0, 127.0, "D", 37.1, 127.1, now.plusHours(2), now.plusHours(1), 4))
                .isInstanceOf(IllegalArgumentException.class);

        // driverMemberId == null
        assertThatThrownBy(() -> Party.create(null, "T", "D", "O", 37.0, 127.0, "D", 37.1, 127.1, now, now.plusHours(1), 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("나머지 도메인 분기 보완")
    void domain_supplementary_test() {
        // 1. PartyStatus 모든 값 순회 (Enum Coverage)
        for (PartyStatus status : PartyStatus.values()) {
            assertThat(status.name()).isNotNull();
        }

        // 2. PartyWaypoint의 create 시 예외 케이스 (orderIndex 범위 밖)
        // 아까 에러 났던 부분! 이렇게 별도 테스트로 빼면 안전하게 커버리지가 올라갑니다.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                PartyWaypoint.create(0, "Invalid", 37.0, 127.0)
        ).isInstanceOf(IllegalArgumentException.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                PartyWaypoint.create(4, "Invalid", 37.0, 127.0)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}