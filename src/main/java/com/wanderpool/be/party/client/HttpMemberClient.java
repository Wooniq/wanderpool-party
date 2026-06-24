package com.wanderpool.be.party.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "party.member-client.type", havingValue = "http")
public class HttpMemberClient implements MemberClient {

    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestTemplate restTemplate;

    @Value("${member.service.url:}")
    private String memberServiceUrl;

    @Value("${party.member-client.internal-token:}")
    private String internalServiceToken;

    @Override
    public String getRole(Long memberId) {
        throw new NonRetryableMemberClientException("member HTTP role lookup is not supported; use gRPC member client", null);
    }

    @Override
    public void debitPoints(PointDebitCommand command) {
        post("/api/members/" + command.memberId() + "/points/debit", PointDebitRequest.from(command), "debit");
    }

    @Override
    public void refundPoints(PointRefundCommand command) {
        post("/api/members/" + command.memberId() + "/points/refund", PointRefundRequest.from(command), "refund");
    }

    @Override
    public void creditPoints(PointCreditCommand command) {
        post("/api/members/" + command.memberId() + "/points/credit", PointCreditRequest.from(command), "credit");
    }

    private void post(String path, Object body, String operationName) {
        if (!StringUtils.hasText(internalServiceToken)) {
            throw new NonRetryableMemberClientException("member internal service token is required", null);
        }
        if (!StringUtils.hasText(memberServiceUrl)) {
            throw new NonRetryableMemberClientException("member service url is required", null);
        }

        try {
            String url = memberServiceUrl + path;
            restTemplate.postForObject(url, requestEntity(body), Void.class);
        } catch (RestClientResponseException e) {
            if ("debit".equals(operationName) && e.getStatusCode().value() == 412) {
                throw new InsufficientPointsMemberClientException(
                        "member HTTP debit request is not retryable: " + e.getStatusCode(), e);
            }
            if (e.getStatusCode().is4xxClientError() && e.getStatusCode().value() != 429) {
                throw new NonRetryableMemberClientException(
                        "member HTTP " + operationName + " request is not retryable: " + e.getStatusCode(), e);
            }
            throw new RetryableMemberClientException("member HTTP " + operationName + " request failed", e);
        } catch (RestClientException e) {
            throw new RetryableMemberClientException("member HTTP " + operationName + " request failed", e);
        }
    }

    private HttpEntity<Object> requestEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(INTERNAL_SERVICE_TOKEN_HEADER, internalServiceToken);
        return new HttpEntity<>(body, headers);
    }
}
