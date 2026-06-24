package com.wanderpool.be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.controller.PartyController;
import com.wanderpool.be.party.service.PartyService;
import com.wanderpool.be.party.service.dto.CancelParticipationRequest;
import com.wanderpool.be.party.service.dto.CancelParticipationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartyController.class)
class CancelParticipationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PartyService partyService;

    @MockBean
    private MemberClient memberClient;

    private CancelParticipationResponse stubResponse() {
        return new CancelParticipationResponse(
                10L, 1L, 1000, 0, PartyStatus.RECRUITING, LocalDateTime.now());
    }

    @Test
    @DisplayName("정상 취소 요청 시 200과 취소 결과가 반환된다")
    void cancelParticipation_success_returns200() throws Exception {
        given(partyService.cancelParticipation(eq(1L), eq(10L), eq(2L), any()))
                .willReturn(stubResponse());

        CancelParticipationRequest request = new CancelParticipationRequest("개인 사정");

        mockMvc.perform(delete("/api/parties/1/participants/10")
                        .header("X-Member-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refundedPoints").value(1000))
                .andExpect(jsonPath("$.data.currentPassengers").value(0))
                .andExpect(jsonPath("$.data.partyStatus").value("RECRUITING"));
    }

    @Test
    @DisplayName("body 없이 취소 요청 시 200이 반환된다")
    void cancelParticipation_noBody_returns200() throws Exception {
        given(partyService.cancelParticipation(eq(1L), eq(10L), eq(2L), isNull()))
                .willReturn(stubResponse());

        mockMvc.perform(delete("/api/parties/1/participants/10")
                        .header("X-Member-Id", 2L))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("참여 정보가 없으면 404가 반환된다")
    void cancelParticipation_notParticipant_returns404() throws Exception {
        given(partyService.cancelParticipation(anyLong(), anyLong(), anyLong(), any()))
                .willThrow(new PartyException(PartyErrorCode.NOT_PARTICIPANT));

        mockMvc.perform(delete("/api/parties/1/participants/999")
                        .header("X-Member-Id", 2L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("본인이 아닌 사용자가 취소 요청 시 403이 반환된다")
    void cancelParticipation_forbidden_returns403() throws Exception {
        given(partyService.cancelParticipation(anyLong(), anyLong(), anyLong(), any()))
                .willThrow(new PartyException(PartyErrorCode.FORBIDDEN));

        mockMvc.perform(delete("/api/parties/1/participants/10")
                        .header("X-Member-Id", 99L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("진행 중 파티 취소 요청 시 409가 반환된다")
    void cancelParticipation_cannotCancel_returns409() throws Exception {
        given(partyService.cancelParticipation(anyLong(), anyLong(), anyLong(), any()))
                .willThrow(new PartyException(PartyErrorCode.CANNOT_CANCEL));

        mockMvc.perform(delete("/api/parties/1/participants/10")
                        .header("X-Member-Id", 2L))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("이미 취소된 참여 재취소 요청 시 409가 반환된다")
    void cancelParticipation_alreadyCancelled_returns409() throws Exception {
        given(partyService.cancelParticipation(anyLong(), anyLong(), anyLong(), any()))
                .willThrow(new PartyException(PartyErrorCode.ALREADY_CANCELLED));

        mockMvc.perform(delete("/api/parties/1/participants/10")
                        .header("X-Member-Id", 2L))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("X-Member-Id 헤더가 없으면 400이 반환된다")
    void cancelParticipation_missingUserIdHeader_returns400() throws Exception {
        mockMvc.perform(delete("/api/parties/1/participants/10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cancelReason이 200자 초과이면 400이 반환된다")
    void cancelParticipation_tooLongReason_returns400() throws Exception {
        String longReason = "a".repeat(201);
        CancelParticipationRequest request = new CancelParticipationRequest(longReason);

        mockMvc.perform(delete("/api/parties/1/participants/10")
                        .header("X-Member-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
