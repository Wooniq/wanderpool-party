package com.wanderpool.be.party.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.service.PartyService;
import com.wanderpool.be.party.service.dto.PartyJoinRequest;
import com.wanderpool.be.party.service.dto.PartyJoinResponse;
import com.wanderpool.be.party.service.dto.PartyParticipantSummaryResponse;
import com.wanderpool.be.party.service.dto.PartyRejectRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartyController.class)
class PartyJoinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PartyService partyService;

    @MockBean
    private MemberClient memberClient;

    private PartyJoinRequest validRequest() {
        return new PartyJoinRequest(
                "강남역 3번 출구", 37.4979, 127.0276,
                "판교역", 1500
        );
    }

    private PartyJoinResponse stubResponse() {
        return new PartyJoinResponse(
                10L, 1L, ParticipantStatus.PENDING, 1500, 0, 3
        );
    }

    @Test
    @DisplayName("파티 참여자 목록 조회 시 200과 목록이 반환된다")
    void getPartyParticipants_success_returns200() throws Exception {
        given(partyService.getPartyParticipants(1L))
                .willReturn(java.util.List.of(
                        new PartyParticipantSummaryResponse(10L, "서울역", ParticipantStatus.PENDING),
                        new PartyParticipantSummaryResponse(11L, "사당역", ParticipantStatus.ACCEPTED)
                ));

        mockMvc.perform(get("/api/parties/{partyId}/participants", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("S003"))
                .andExpect(jsonPath("$.data[0].participantId").value(10))
                .andExpect(jsonPath("$.data[0].pickupName").value("서울역"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[1].status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("존재하지 않는 파티의 참여자 목록 조회 시 404가 반환된다")
    void getPartyParticipants_notFound_returns404() throws Exception {
        given(partyService.getPartyParticipants(999L))
                .willThrow(new PartyException(PartyErrorCode.PARTY_NOT_FOUND));

        mockMvc.perform(get("/api/parties/{partyId}/participants", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PARTY_404"));
    }

    @Test
    @DisplayName("승객이 유효한 요청으로 파티에 참여하면 201과 PENDING 상태가 반환된다")
    void joinParty_success_returns201() throws Exception {
        given(partyService.joinParty(eq(1L), eq(2L), any(PartyJoinRequest.class)))
                .willReturn(stubResponse());

        mockMvc.perform(post("/api/parties/{partyId}/join", 1L)
                        .header("X-Member-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.participantId").value(10))
                .andExpect(jsonPath("$.data.partyId").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.pointCost").value(1500))
                .andExpect(jsonPath("$.data.currentPassengers").value(0))
                .andExpect(jsonPath("$.data.maxPassengers").value(3));
    }

    @Test
    @DisplayName("존재하지 않는 파티에 참여 시 404가 반환된다")
    void joinParty_partyNotFound_returns404() throws Exception {
        given(partyService.joinParty(eq(999L), eq(2L), any(PartyJoinRequest.class)))
                .willThrow(new PartyException(PartyErrorCode.PARTY_NOT_FOUND));

        mockMvc.perform(post("/api/parties/{partyId}/join", 999L)
                        .header("X-Member-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PARTY_404"));
    }

    @Test
    @DisplayName("본인 파티에 참여 시도 시 400이 반환된다")
    void joinParty_ownParty_returns400() throws Exception {
        given(partyService.joinParty(eq(1L), eq(1L), any(PartyJoinRequest.class)))
                .willThrow(new PartyException(PartyErrorCode.CANNOT_JOIN_OWN_PARTY));

        mockMvc.perform(post("/api/parties/{partyId}/join", 1L)
                        .header("X-Member-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PARTICIPANT_400_1"));
    }

    @Test
    @DisplayName("이미 참여한 파티에 재요청 시 409가 반환된다")
    void joinParty_alreadyJoined_returns409() throws Exception {
        given(partyService.joinParty(eq(1L), eq(2L), any(PartyJoinRequest.class)))
                .willThrow(new PartyException(PartyErrorCode.ALREADY_JOINED));

        mockMvc.perform(post("/api/parties/{partyId}/join", 1L)
                        .header("X-Member-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PARTICIPANT_409_1"));
    }

    @Test
    @DisplayName("정원 초과 시 409가 반환된다")
    void joinParty_capacityExceeded_returns409() throws Exception {
        given(partyService.joinParty(eq(1L), eq(2L), any(PartyJoinRequest.class)))
                .willThrow(new PartyException(PartyErrorCode.CAPACITY_EXCEEDED));

        mockMvc.perform(post("/api/parties/{partyId}/join", 1L)
                        .header("X-Member-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PARTICIPANT_409_2"));
    }

    @Test
    @DisplayName("pickupName이 blank이면 400이 반환된다")
    void joinParty_blankPickupName_returns400() throws Exception {
        PartyJoinRequest badRequest = new PartyJoinRequest(
                "", 37.4979, 127.0276, "판교역", 1500
        );

        mockMvc.perform(post("/api/parties/{partyId}/join", 1L)
                        .header("X-Member-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("pointCost가 음수이면 400이 반환된다")
    void joinParty_negativePointCost_returns400() throws Exception {
        PartyJoinRequest badRequest = new PartyJoinRequest(
                "강남역", 37.4979, 127.0276, "판교역", -100
        );

        mockMvc.perform(post("/api/parties/{partyId}/join", 1L)
                        .header("X-Member-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("pickupLat이 범위를 벗어나면 400이 반환된다")
    void joinParty_invalidLatitude_returns400() throws Exception {
        PartyJoinRequest badRequest = new PartyJoinRequest(
                "강남역", 999.0, 127.0276, "판교역", 1500
        );

        mockMvc.perform(post("/api/parties/{partyId}/join", 1L)
                        .header("X-Member-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("X-Member-Id 헤더가 없으면 400이 반환된다")
    void joinParty_missingMemberIdHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/parties/{partyId}/join", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    // ───────────── accept / reject ─────────────

    @Test
    @DisplayName("Driver가 참여 요청을 승낙하면 200과 ACCEPTED 응답이 반환된다")
    void acceptJoinRequest_success_returns200() throws Exception {
        PartyJoinResponse stub = new PartyJoinResponse(
                10L, 1L, ParticipantStatus.ACCEPTED, 1500, 1, 3
        );
        given(partyService.acceptJoinRequest(eq(1L), eq(10L), eq(1L)))
                .willReturn(stub);

        mockMvc.perform(post("/api/parties/{partyId}/participants/{participantId}/accept", 1L, 10L)
                        .header("X-Member-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.currentPassengers").value(1));
    }

    @Test
    @DisplayName("Driver가 아닌 사용자가 승낙을 시도하면 403이 반환된다")
    void acceptJoinRequest_notDriver_returns403() throws Exception {
        given(partyService.acceptJoinRequest(eq(1L), eq(10L), eq(99L)))
                .willThrow(new PartyException(PartyErrorCode.NOT_PARTY_DRIVER));

        mockMvc.perform(post("/api/parties/{partyId}/participants/{participantId}/accept", 1L, 10L)
                        .header("X-Member-Id", 99L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PARTY_403_2"));
    }

    @Test
    @DisplayName("이미 ACCEPTED된 참여 요청을 다시 승낙하면 409가 반환된다")
    void acceptJoinRequest_notPending_returns409() throws Exception {
        given(partyService.acceptJoinRequest(eq(1L), eq(10L), eq(1L)))
                .willThrow(new PartyException(PartyErrorCode.PARTICIPANT_NOT_PENDING));

        mockMvc.perform(post("/api/parties/{partyId}/participants/{participantId}/accept", 1L, 10L)
                        .header("X-Member-Id", 1L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTICIPANT_409_6"));
    }

    @Test
    @DisplayName("존재하지 않는 참여 요청 승낙 시 404가 반환된다")
    void acceptJoinRequest_notFound_returns404() throws Exception {
        given(partyService.acceptJoinRequest(eq(1L), eq(999L), eq(1L)))
                .willThrow(new PartyException(PartyErrorCode.NOT_PARTICIPANT));

        mockMvc.perform(post("/api/parties/{partyId}/participants/{participantId}/accept", 1L, 999L)
                        .header("X-Member-Id", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARTICIPANT_404_1"));
    }

    @Test
    @DisplayName("Driver가 참여 요청을 거절하면 200과 REJECTED 응답이 반환된다 (사유 포함)")
    void rejectJoinRequest_success_returns200() throws Exception {
        PartyJoinResponse stub = new PartyJoinResponse(
                10L, 1L, ParticipantStatus.REJECTED, 1500, 0, 3
        );
        given(partyService.rejectJoinRequest(eq(1L), eq(10L), eq(1L), any(PartyRejectRequest.class)))
                .willReturn(stub);

        mockMvc.perform(post("/api/parties/{partyId}/participants/{participantId}/reject", 1L, 10L)
                        .header("X-Member-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"경로 이탈\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("거절 요청 body 없이도 200이 반환된다")
    void rejectJoinRequest_noBody_returns200() throws Exception {
        PartyJoinResponse stub = new PartyJoinResponse(
                10L, 1L, ParticipantStatus.REJECTED, 1500, 0, 3
        );
        given(partyService.rejectJoinRequest(eq(1L), eq(10L), eq(1L), org.mockito.ArgumentMatchers.isNull()))
                .willReturn(stub);

        mockMvc.perform(post("/api/parties/{partyId}/participants/{participantId}/reject", 1L, 10L)
                        .header("X-Member-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }
}
