package com.wanderpool.be.party.controller;

import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.service.PartyService;
import com.wanderpool.be.party.service.dto.UpdatePartyStatusResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartyController.class)
class PartyStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PartyService partyService;

    @Test
    @DisplayName("Driver가 운행 시작 요청 시 200과 IN_PROGRESS 상태가 반환된다")
    void startParty_success_returns200() throws Exception {
        given(partyService.startParty(eq(1L), eq(10L)))
                .willReturn(new UpdatePartyStatusResponse(1L, PartyStatus.IN_PROGRESS));

        mockMvc.perform(post("/api/parties/1/in-progress")
                        .header("X-Member-Id", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partyId").value(1))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("Driver가 아닌 멤버가 운행 시작 요청 시 403이 반환된다")
    void startParty_notDriver_returns403() throws Exception {
        given(partyService.startParty(anyLong(), anyLong()))
                .willThrow(new PartyException(PartyErrorCode.NOT_PARTY_DRIVER));

        mockMvc.perform(post("/api/parties/1/in-progress")
                        .header("X-Member-Id", 99L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 파티에 운행 시작 요청 시 404가 반환된다")
    void startParty_partyNotFound_returns404() throws Exception {
        given(partyService.startParty(anyLong(), anyLong()))
                .willThrow(new PartyException(PartyErrorCode.PARTY_NOT_FOUND));

        mockMvc.perform(post("/api/parties/999/in-progress")
                        .header("X-Member-Id", 10L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("허용되지 않는 상태에서 운행 시작 요청 시 400이 반환된다")
    void startParty_invalidTransition_returns400() throws Exception {
        given(partyService.startParty(anyLong(), anyLong()))
                .willThrow(new PartyException(PartyErrorCode.INVALID_STATUS_TRANSITION));

        mockMvc.perform(post("/api/parties/1/in-progress")
                        .header("X-Member-Id", 10L))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("X-Member-Id 헤더 없이 운행 시작 요청 시 400이 반환된다")
    void startParty_missingHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/parties/1/in-progress"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Driver가 운행 종료 요청 시 200과 COMPLETED 상태가 반환된다")
    void completeParty_success_returns200() throws Exception {
        given(partyService.completeParty(eq(1L), eq(10L)))
                .willReturn(new UpdatePartyStatusResponse(1L, PartyStatus.COMPLETED));

        mockMvc.perform(post("/api/parties/1/complete")
                        .header("X-Member-Id", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partyId").value(1))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Driver가 아닌 멤버가 운행 종료 요청 시 403이 반환된다")
    void completeParty_notDriver_returns403() throws Exception {
        given(partyService.completeParty(anyLong(), anyLong()))
                .willThrow(new PartyException(PartyErrorCode.NOT_PARTY_DRIVER));

        mockMvc.perform(post("/api/parties/1/complete")
                        .header("X-Member-Id", 99L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("허용되지 않는 상태에서 운행 종료 요청 시 400이 반환된다")
    void completeParty_invalidTransition_returns400() throws Exception {
        given(partyService.completeParty(anyLong(), anyLong()))
                .willThrow(new PartyException(PartyErrorCode.INVALID_STATUS_TRANSITION));

        mockMvc.perform(post("/api/parties/1/complete")
                        .header("X-Member-Id", 10L))
                .andExpect(status().isBadRequest());
    }
}
