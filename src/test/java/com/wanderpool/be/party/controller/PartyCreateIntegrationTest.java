package com.wanderpool.be.party.controller;

import com.wanderpool.be.party.client.MemberClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "grpc.server.port=0")
@AutoConfigureMockMvc
class PartyCreateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemberClient memberClient;

    @Test
    @DisplayName("파티 생성 시 JPA 감사 필드가 채워져 201 응답을 반환한다")
    void createParty_persistsWithAuditingFields() throws Exception {
        given(memberClient.getRole(1L)).willReturn("DRIVER");

        String requestBody = """
                {
                  "title": "출근 카풀 - 양재에서 판교",
                  "description": "양재역에서 출발해서 판교테크노밸리까지 이동합니다.",
                  "originName": "양재역 9번 출구",
                  "originLat": 37.4845,
                  "originLng": 127.0344,
                  "destinationName": "판교테크노밸리",
                  "destLat": 37.4,
                  "destLng": 127.1087,
                  "departureTime": "2026-05-13T08:30:00",
                  "arrivalTime": "2026-05-13T09:20:00",
                  "maxPassengers": 3,
                  "waypoints": [
                    {
                      "orderIndex": 1,
                      "name": "강남역 5번 출구",
                      "latitude": 37.4979,
                      "longitude": 127.0276
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/parties")
                        .header("X-Member-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.originName").value("양재역 9번 출구"))
                .andExpect(jsonPath("$.data.waypointCount").value(1));
    }
}
