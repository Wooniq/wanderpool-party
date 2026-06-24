package com.wanderpool.be.party.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpMemberClientTest {

    private static final PointRefundCommand COMMAND = new PointRefundCommand(
            10L,
            1500,
            20L,
            30L,
            "party-cancel:20:30"
    );

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private HttpMemberClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new HttpMemberClient(restTemplate);
        ReflectionTestUtils.setField(client, "memberServiceUrl", "http://member-service");
        ReflectionTestUtils.setField(client, "internalServiceToken", "internal-token");
    }

    @Test
    void refundPoints_postsRefundRequestWithInternalToken() {
        server.expect(once(), requestTo("http://member-service/api/members/10/points/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "internal-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "amount": 1500,
                          "requestId": "party-cancel:20:30",
                          "partyId": 20,
                          "participantId": 30
                        }
                        """))
                .andRespond(withSuccess());

        client.refundPoints(COMMAND);

        server.verify();
    }

    @Test
    void debitPoints_preconditionFailedThrowsInsufficientPointsException() {
        PointDebitCommand command = new PointDebitCommand(
                10L,
                1500,
                20L,
                30L,
                "party-join:20:10"
        );
        server.expect(once(), requestTo("http://member-service/api/members/10/points/debit"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "internal-token"))
                .andExpect(content().json("""
                        {
                          "amount": 1500,
                          "requestId": "party-join:20:10",
                          "partyId": 20,
                          "participantId": 30
                        }
                        """))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        assertThatThrownBy(() -> client.debitPoints(command))
                .isInstanceOf(InsufficientPointsMemberClientException.class)
                .hasMessageContaining("412 PRECONDITION_FAILED");

        server.verify();
    }

    @Test
    void creditPoints_postsCreditRequestWithInternalToken() {
        PointCreditCommand command = new PointCreditCommand(
                10L,
                1500,
                20L,
                "party-complete:20:driver-credit"
        );
        server.expect(once(), requestTo("http://member-service/api/members/10/points/credit"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "internal-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "amount": 1500,
                          "requestId": "party-complete:20:driver-credit",
                          "partyId": 20
                        }
                        """))
                .andRespond(withSuccess());

        client.creditPoints(command);

        server.verify();
    }

    @Test
    void refundPoints_blankInternalTokenFailsFast() {
        ReflectionTestUtils.setField(client, "internalServiceToken", " ");

        assertThatThrownBy(() -> client.refundPoints(COMMAND))
                .isInstanceOf(NonRetryableMemberClientException.class)
                .hasMessage("member internal service token is required");

        server.verify();
    }

    @Test
    void refundPoints_blankMemberServiceUrlFailsFast() {
        ReflectionTestUtils.setField(client, "memberServiceUrl", " ");

        assertThatThrownBy(() -> client.refundPoints(COMMAND))
                .isInstanceOf(NonRetryableMemberClientException.class)
                .hasMessage("member service url is required");

        server.verify();
    }

    @Test
    void refundPoints_serverErrorThrowsRetryableException() {
        server.expect(once(), requestTo("http://member-service/api/members/10/points/refund"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.refundPoints(COMMAND))
                .isInstanceOf(RetryableMemberClientException.class);
    }

    @Test
    void refundPoints_badRequestThrowsNonRetryableException() {
        server.expect(once(), requestTo("http://member-service/api/members/10/points/refund"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.refundPoints(COMMAND))
                .isInstanceOf(NonRetryableMemberClientException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void refundPoints_tooManyRequestsThrowsRetryableException() {
        server.expect(once(), requestTo("http://member-service/api/members/10/points/refund"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.refundPoints(COMMAND))
                .isInstanceOf(RetryableMemberClientException.class);
    }
}
