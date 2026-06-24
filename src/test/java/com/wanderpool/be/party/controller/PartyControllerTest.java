package com.wanderpool.be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.controller.PartyController;
import com.wanderpool.be.party.service.dto.PartyCreateRequest;
import com.wanderpool.be.party.service.dto.PartyCreateResponse;
import com.wanderpool.be.party.service.dto.PartyDetailResponse;
import com.wanderpool.be.party.service.dto.PartySearchRequest;
import com.wanderpool.be.party.service.dto.PartySummaryResponse;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.service.PartyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartyController.class)
class PartyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PartyService partyService;

    @MockBean
    private MemberClient memberClient;

    private final LocalDateTime departure = LocalDateTime.now().plusHours(1);
    private final LocalDateTime arrival = departure.plusHours(2);

    private PartySummaryResponse summaryResponse() {
        return new PartySummaryResponse(
                1L,
                "강남 → 판교",
                "강남역",
                37.4979,
                127.0276,
                "판교역",
                37.3949,
                127.1112,
                departure,
                arrival,
                1,
                3,
                PartyStatus.RECRUITING,
                List.of(
                        new PartySummaryResponse.PartyWaypointSummaryResponse(
                                1,
                                "양재역",
                                37.4845,
                                127.0344
                        )
                )
        );
    }

    private PartyDetailResponse detailResponse() {
        return new PartyDetailResponse(
                1L,
                "강남 → 판교",
                "출퇴근 카풀",
                PartyStatus.RECRUITING,
                "강남역",
                37.4979,
                127.0276,
                departure,
                List.of(
                        new PartyDetailResponse.PartyWaypointDetailResponse(
                                1,
                                "양재역",
                                37.4845,
                                127.0344,
                                departure.plusMinutes(15)
                        ),
                        new PartyDetailResponse.PartyWaypointDetailResponse(
                                2,
                                "판교IC",
                                37.3999,
                                127.1000,
                                departure.plusMinutes(45)
                        )
                ),
                "판교역",
                37.3949,
                127.1112,
                arrival,
                3,
                1
        );
    }

    private PartyCreateRequest validRequest() {
        return new PartyCreateRequest(
                "강남 → 판교", "출퇴근 카풀",
                "강남역", 37.4979, 127.0276,
                "판교역", 37.3949, 127.1112,
                departure, arrival, 3, null
        );
    }

    private PartyCreateResponse stubResponse() {
        return new PartyCreateResponse(
                1L, PartyStatus.RECRUITING, "강남 → 판교",
                "강남역", 37.4979, 127.0276,
                "판교역", 37.3949, 127.1112,
                departure, arrival, 3, 0, 0
        );
    }

    @Test
    @DisplayName("Driver가 유효한 요청으로 파티를 생성하면 201과 응답 바디가 반환된다")
    void createParty_success_returns201() throws Exception {
        given(partyService.createParty(eq(1L), any(PartyCreateRequest.class)))
                .willReturn(stubResponse());

        mockMvc.perform(post("/api/parties")
                        .header("X-Member-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.partyId").value(1))
                .andExpect(jsonPath("$.data.status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.originName").value("강남역"))
                .andExpect(jsonPath("$.data.destinationName").value("판교역"));
    }

    @Test
    @DisplayName("조건 없이 파티를 조회하면 200과 목록이 반환된다")
    void searchParties_withoutConditions_returns200() throws Exception {
        given(partyService.searchParties(any(PartySearchRequest.class)))
                .willReturn(List.of(summaryResponse()));

        mockMvc.perform(get("/api/parties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("S003"))
                .andExpect(jsonPath("$.data[0].partyId").value(1))
                .andExpect(jsonPath("$.data[0].originLat").value(37.4979))
                .andExpect(jsonPath("$.data[0].originLng").value(127.0276))
                .andExpect(jsonPath("$.data[0].status").value("RECRUITING"))
                .andExpect(jsonPath("$.data[0].waypoints[0].orderIndex").value(1))
                .andExpect(jsonPath("$.data[0].waypoints[0].name").value("양재역"))
                .andExpect(jsonPath("$.data[0].waypoints[0].latitude").value(37.4845))
                .andExpect(jsonPath("$.data[0].waypoints[0].longitude").value(127.0344));
    }

    @Test
    @DisplayName("상태와 시간 조건으로 파티를 조회할 수 있다")
    void searchParties_withConditions_returnsFilteredList() throws Exception {
        given(partyService.searchParties(any(PartySearchRequest.class)))
                .willReturn(List.of(summaryResponse()));

        mockMvc.perform(get("/api/parties")
                        .param("originName", "강남")
                        .param("destinationName", "판교")
                        .param("departureAfter", departure.minusMinutes(30).toString())
                        .param("arrivalBefore", arrival.plusMinutes(30).toString())
                        .param("status", "RECRUITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].originName").value("강남역"))
                .andExpect(jsonPath("$.data[0].destinationName").value("판교역"));
    }

    @Test
    @DisplayName("내가 생성한 파티 목록을 조회하면 200과 목록이 반환된다")
    void getMyCreatedParties_returns200() throws Exception {
        given(partyService.getCreatedParties(1L))
                .willReturn(List.of(summaryResponse()));

        mockMvc.perform(get("/api/parties/me/created")
                        .header("X-Member-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("S003"))
                .andExpect(jsonPath("$.data[0].partyId").value(1))
                .andExpect(jsonPath("$.data[0].title").value("강남 → 판교"))
                .andExpect(jsonPath("$.data[0].waypoints[0].name").value("양재역"));
    }

    @Test
    @DisplayName("내가 생성한 파티가 없으면 200과 빈 배열이 반환된다")
    void getMyCreatedParties_empty_returns200() throws Exception {
        given(partyService.getCreatedParties(1L))
                .willReturn(List.of());

        mockMvc.perform(get("/api/parties/me/created")
                        .header("X-Member-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("S003"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("내가 참여한 파티 목록을 조회하면 200과 목록이 반환된다")
    void getMyJoinedParties_returns200() throws Exception {
        given(partyService.getJoinedParties(2L))
                .willReturn(List.of(summaryResponse()));

        mockMvc.perform(get("/api/parties/me/joined")
                        .header("X-Member-Id", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("S003"))
                .andExpect(jsonPath("$.data[0].partyId").value(1))
                .andExpect(jsonPath("$.data[0].status").value("RECRUITING"))
                .andExpect(jsonPath("$.data[0].waypoints[0].name").value("양재역"));
    }

    @Test
    @DisplayName("내가 참여한 파티가 없으면 200과 빈 배열이 반환된다")
    void getMyJoinedParties_empty_returns200() throws Exception {
        given(partyService.getJoinedParties(2L))
                .willReturn(List.of());

        mockMvc.perform(get("/api/parties/me/joined")
                        .header("X-Member-Id", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("S003"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("파티 상세를 조회하면 200과 응답 바디가 반환된다")
    void getParty_success_returns200() throws Exception {
        given(partyService.getParty(1L))
                .willReturn(detailResponse());

        mockMvc.perform(get("/api/parties/{partyId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.partyId").value(1))
                .andExpect(jsonPath("$.data.title").value("강남 → 판교"))
                .andExpect(jsonPath("$.data.originLat").value(37.4979))
                .andExpect(jsonPath("$.data.originLng").value(127.0276))
                .andExpect(jsonPath("$.data.waypoints[0].orderIndex").value(1))
                .andExpect(jsonPath("$.data.waypoints[0].latitude").value(37.4845))
                .andExpect(jsonPath("$.data.waypoints[0].longitude").value(127.0344))
                .andExpect(jsonPath("$.data.waypoints[0].name").value("양재역"))
                .andExpect(jsonPath("$.data.destinationName").value("판교역"));
    }

    @Test
    @DisplayName("존재하지 않는 파티 상세 조회 시 404가 반환된다")
    void getParty_notFound_returns404() throws Exception {
        given(partyService.getParty(999L))
                .willThrow(new PartyException(PartyErrorCode.PARTY_NOT_FOUND));

        mockMvc.perform(get("/api/parties/{partyId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PARTY_404"));
    }

    @Test
    @DisplayName("Driver가 아닌 역할로 요청하면 서비스 예외가 403으로 반환된다")
    void createParty_notDriver_returns403() throws Exception {
        given(partyService.createParty(eq(2L), any(PartyCreateRequest.class)))
                .willThrow(new PartyException(PartyErrorCode.NOT_DRIVER));

        mockMvc.perform(post("/api/parties")
                        .header("X-Member-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("originName이 blank이면 400이 반환된다")
    void createParty_blankOriginName_returns400() throws Exception {
        PartyCreateRequest badRequest = new PartyCreateRequest(
                "제목", "설명",
                "", 37.4979, 127.0276,
                "판교역", 37.3949, 127.1112,
                departure, arrival, 3, null
        );

        mockMvc.perform(post("/api/parties")
                        .header("X-Member-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("maxPassengers가 0이면 400이 반환된다")
    void createParty_zeroMaxPassengers_returns400() throws Exception {
        PartyCreateRequest badRequest = new PartyCreateRequest(
                "제목", "설명",
                "강남역", 37.4979, 127.0276,
                "판교역", 37.3949, 127.1112,
                departure, arrival, 0, null
        );

        mockMvc.perform(post("/api/parties")
                        .header("X-Member-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("X-Member-Id 헤더가 없으면 400이 반환된다")
    void createParty_missingMemberIdHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }
}
