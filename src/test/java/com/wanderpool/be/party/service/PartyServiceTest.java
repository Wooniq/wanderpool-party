package com.wanderpool.be.service;

import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.domain.PartyWaypoint;
import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.credit.PointCreditOutboxRepository;
import com.wanderpool.be.party.refund.PointRefundOutboxRepository;
import com.wanderpool.be.party.service.PartyService;
import com.wanderpool.be.party.service.dto.PartyCreateRequest;
import com.wanderpool.be.party.service.dto.PartyCreateResponse;
import com.wanderpool.be.party.service.dto.PartyDetailResponse;
import com.wanderpool.be.party.service.dto.PartySearchRequest;
import com.wanderpool.be.party.service.dto.PartySummaryResponse;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.repository.PartyParticipantRepository;
import com.wanderpool.be.party.repository.PartyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PartyServiceTest {

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

    private PartyCreateRequest buildRequest(List<PartyCreateRequest.WaypointRequest> waypoints) {
        return new PartyCreateRequest(
                "강남 → 판교",
                "출퇴근 카풀",
                "강남역", 37.4979, 127.0276,
                "판교역", 37.3949, 127.1112,
                departure, arrival, 3,
                waypoints
        );
    }

    @Test
    @DisplayName("Driver가 파티를 생성하면 RECRUITING 상태의 응답이 반환된다")
    void createParty_success() {
        PartyCreateRequest request = buildRequest(null);
        given(memberClient.getRole(1L)).willReturn("DRIVER");

        PartyCreateResponse response = partyService.createParty(1L, request);

        assertThat(response.status()).isEqualTo(PartyStatus.RECRUITING);
        assertThat(response.originName()).isEqualTo("강남역");
        assertThat(response.originLat()).isEqualTo(37.4979);
        assertThat(response.originLng()).isEqualTo(127.0276);
        assertThat(response.destinationName()).isEqualTo("판교역");
        assertThat(response.destLat()).isEqualTo(37.3949);
        assertThat(response.destLng()).isEqualTo(127.1112);
        assertThat(response.maxPassengers()).isEqualTo(3);
        assertThat(response.currentPassengers()).isZero();
        assertThat(response.waypointCount()).isZero();
        verify(partyRepository).save(any(Party.class));
    }

    @Test
    @DisplayName("경유지를 포함하여 파티를 생성하면 waypointCount가 반영된다")
    void createParty_withWaypoints() {
        List<PartyCreateRequest.WaypointRequest> waypoints = List.of(
                new PartyCreateRequest.WaypointRequest(1, "양재역", 37.4845, 127.0344),
                new PartyCreateRequest.WaypointRequest(2, "판교IC", 37.3999, 127.1000)
        );
        PartyCreateRequest request = buildRequest(waypoints);
        given(memberClient.getRole(1L)).willReturn("DRIVER");

        PartyCreateResponse response = partyService.createParty(1L, request);

        assertThat(response.waypointCount()).isEqualTo(2);
        verify(partyRepository).save(any(Party.class));
    }

    @Test
    @DisplayName("Passenger 역할로 파티 생성 시도 시 PartyException이 발생한다")
    void createParty_notDriver_throwsException() {
        PartyCreateRequest request = buildRequest(null);
        given(memberClient.getRole(2L)).willReturn("PASSENGER");

        assertThatThrownBy(() -> partyService.createParty(2L, request))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(partyRepository);
    }

    @Test
    @DisplayName("member role 조회가 실패하면 PartyException이 발생한다")
    void createParty_memberRoleLookupFailure_throwsException() {
        PartyCreateRequest request = buildRequest(null);
        given(memberClient.getRole(1L))
                .willThrow(new com.wanderpool.be.party.client.RetryableMemberClientException("timeout", null));

        assertThatThrownBy(() -> partyService.createParty(1L, request))
                .isInstanceOf(PartyException.class);

        verifyNoInteractions(partyRepository);
    }

    @Test
    @DisplayName("도착 시간이 출발 시간보다 빠르면 파티 생성에 실패한다")
    void createParty_invalidDepartureArrival_throwsException() {
        PartyCreateRequest request = new PartyCreateRequest(
                "제목", "설명",
                "출발지", 37.0, 127.0,
                "목적지", 37.1, 127.1,
                arrival, departure,
                3, null
        );

        given(memberClient.getRole(1L)).willReturn("DRIVER");

        assertThatThrownBy(() -> partyService.createParty(1L, request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(partyRepository);
    }

    @Test
    @DisplayName("검색 조건으로 파티를 조회하면 요약 응답 목록으로 변환된다")
    void searchParties_returnsSummaryResponses() {
        PartySearchRequest request = new PartySearchRequest(
                "강남",
                "판교",
                departure.minusMinutes(30),
                arrival.plusMinutes(30),
                PartyStatus.RECRUITING
        );

        Party party = Party.create(
                1L,
                "강남 → 판교",
                "출퇴근 카풀",
                "강남역",
                37.4979,
                127.0276,
                "판교역",
                37.3949,
                127.1112,
                departure,
                arrival,
                3
        );
        party.addWaypoint(PartyWaypoint.create(2, "판교IC", 37.3999, 127.1000));
        party.addWaypoint(PartyWaypoint.create(1, "양재역", 37.4845, 127.0344));

        given(partyRepository.findAll(any(Specification.class))).willReturn(List.of(party));

        List<PartySummaryResponse> responses = partyService.searchParties(request);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().originName()).isEqualTo("강남역");
        assertThat(responses.getFirst().destinationName()).isEqualTo("판교역");
        assertThat(responses.getFirst().status()).isEqualTo(PartyStatus.RECRUITING);
        assertThat(responses.getFirst().waypoints()).hasSize(2);
        assertThat(responses.getFirst().waypoints().get(0).orderIndex()).isEqualTo(1);
        assertThat(responses.getFirst().waypoints().get(0).name()).isEqualTo("양재역");
        assertThat(responses.getFirst().waypoints().get(0).latitude()).isEqualTo(37.4845);
        assertThat(responses.getFirst().waypoints().get(0).longitude()).isEqualTo(127.0344);
        assertThat(responses.getFirst().waypoints().get(1).orderIndex()).isEqualTo(2);
        assertThat(responses.getFirst().waypoints().get(1).name()).isEqualTo("판교IC");
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 목록을 반환한다")
    void searchParties_emptyResult_returnsEmptyList() {
        PartySearchRequest request = new PartySearchRequest(null, null, null, null, null);

        given(partyRepository.findAll(any(Specification.class))).willReturn(List.of());

        List<PartySummaryResponse> responses = partyService.searchParties(request);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("내가 생성한 파티 목록을 조회하면 요약 응답 목록으로 변환된다")
    void getCreatedParties_returnsSummaryResponses() {
        Party party = Party.create(
                1L,
                "강남 → 판교",
                "출퇴근 카풀",
                "강남역",
                37.4979,
                127.0276,
                "판교역",
                37.3949,
                127.1112,
                departure,
                arrival,
                3
        );

        given(partyRepository.findCreatedParties(1L)).willReturn(List.of(party));

        List<PartySummaryResponse> responses = partyService.getCreatedParties(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().title()).isEqualTo("강남 → 판교");
        assertThat(responses.getFirst().status()).isEqualTo(PartyStatus.RECRUITING);
    }

    @Test
    @DisplayName("내가 생성한 파티가 없으면 빈 목록을 반환한다")
    void getCreatedParties_emptyResult_returnsEmptyList() {
        given(partyRepository.findCreatedParties(1L)).willReturn(List.of());

        List<PartySummaryResponse> responses = partyService.getCreatedParties(1L);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("내가 참여한 파티 목록은 ACCEPTED 상태만 조회한다")
    void getJoinedParties_returnsAcceptedSummaryResponses() {
        Party party = Party.create(
                9L,
                "잠실 → 판교",
                "퇴근 카풀",
                "잠실역",
                37.5133,
                127.1002,
                "판교역",
                37.3949,
                127.1112,
                departure,
                arrival,
                4
        );

        given(partyRepository.findJoinedParties(2L, ParticipantStatus.ACCEPTED))
                .willReturn(List.of(party));

        List<PartySummaryResponse> responses = partyService.getJoinedParties(2L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().title()).isEqualTo("잠실 → 판교");
        assertThat(responses.getFirst().originName()).isEqualTo("잠실역");
    }

    @Test
    @DisplayName("내가 참여한 파티가 없으면 빈 목록을 반환한다")
    void getJoinedParties_emptyResult_returnsEmptyList() {
        given(partyRepository.findJoinedParties(2L, ParticipantStatus.ACCEPTED))
                .willReturn(List.of());

        List<PartySummaryResponse> responses = partyService.getJoinedParties(2L);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("파티 상세 조회 시 상세 응답으로 변환된다")
    void getParty_returnsDetailResponse() {
        Party party = Party.create(
                1L,
                "강남 → 판교",
                "출퇴근 카풀",
                "강남역",
                37.4979,
                127.0276,
                "판교역",
                37.3949,
                127.1112,
                departure,
                arrival,
                3
        );
        PartyWaypoint secondWaypoint = PartyWaypoint.create(2, "판교IC", 37.3999, 127.1000);
        secondWaypoint.updateEstimatedArrival(departure.plusMinutes(45));
        PartyWaypoint firstWaypoint = PartyWaypoint.create(1, "양재역", 37.4845, 127.0344);
        firstWaypoint.updateEstimatedArrival(departure.plusMinutes(15));
        party.addWaypoint(secondWaypoint);
        party.addWaypoint(firstWaypoint);

        given(partyRepository.findById(1L)).willReturn(java.util.Optional.of(party));

        PartyDetailResponse response = partyService.getParty(1L);

        assertThat(response.title()).isEqualTo("강남 → 판교");
        assertThat(response.description()).isEqualTo("출퇴근 카풀");
        assertThat(response.originName()).isEqualTo("강남역");
        assertThat(response.originLat()).isEqualTo(37.4979);
        assertThat(response.originLng()).isEqualTo(127.0276);
        assertThat(response.destinationName()).isEqualTo("판교역");
        assertThat(response.destLat()).isEqualTo(37.3949);
        assertThat(response.destLng()).isEqualTo(127.1112);
        assertThat(response.arrivalTime()).isEqualTo(arrival);
        assertThat(response.currentPassengers()).isZero();
        assertThat(response.waypoints()).hasSize(2);
        assertThat(response.waypoints().get(0).orderIndex()).isEqualTo(1);
        assertThat(response.waypoints().get(0).name()).isEqualTo("양재역");
        assertThat(response.waypoints().get(0).latitude()).isEqualTo(37.4845);
        assertThat(response.waypoints().get(0).longitude()).isEqualTo(127.0344);
        assertThat(response.waypoints().get(1).orderIndex()).isEqualTo(2);
        assertThat(response.waypoints().get(1).latitude()).isEqualTo(37.3999);
        assertThat(response.waypoints().get(1).longitude()).isEqualTo(127.1000);
    }

    @Test
    @DisplayName("존재하지 않는 파티 상세 조회 시 PartyException이 발생한다")
    void getParty_notFound_throwsException() {
        given(partyRepository.findById(999L)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> partyService.getParty(999L))
                .isInstanceOf(PartyException.class)
                .hasMessage(PartyErrorCode.PARTY_NOT_FOUND.getMessage());
    }
}
