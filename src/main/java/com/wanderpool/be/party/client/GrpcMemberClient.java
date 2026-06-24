package com.wanderpool.be.party.client;

import com.wanderpool.be.grpc.member.MemberPointServiceGrpc;
import com.wanderpool.be.grpc.member.GetMemberRoleRequest;
import com.wanderpool.be.grpc.member.GetMemberRoleResponse;
import com.wanderpool.be.grpc.member.MemberRole;
import com.wanderpool.be.grpc.member.PointCreditRequest;
import com.wanderpool.be.grpc.member.PointDebitRequest;
import com.wanderpool.be.grpc.member.PointRefundRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "party.member-client.type", havingValue = "grpc", matchIfMissing = true)
public class GrpcMemberClient implements MemberClient {

    @GrpcClient("member-service")
    private MemberPointServiceGrpc.MemberPointServiceBlockingStub stub;

    @Value("${party.member-client.timeout-ms:500}")
    private long timeoutMs;

    @Override
    public String getRole(Long memberId) {
        try {
            GetMemberRoleResponse response = stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                    .getMemberRole(GetMemberRoleRequest.newBuilder()
                            .setMemberId(memberId)
                            .build());

            return toApiRole(response.getRole());
        } catch (StatusRuntimeException e) {
            throw translateException("member gRPC role lookup failure", e);
        }
    }

    @Override
    public void debitPoints(PointDebitCommand command) {
        try {
            PointDebitRequest.Builder request = PointDebitRequest.newBuilder()
                    .setMemberId(command.memberId())
                    .setAmount(command.amount())
                    .setPartyId(command.partyId())
                    .setRequestId(command.requestId());
            if (command.participantId() != null) {
                request.setParticipantId(command.participantId());
            }
            stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                    .debitPoints(request.build());
        } catch (StatusRuntimeException e) {
            throw translateException("member gRPC debit failure", e);
        }
    }

    @Override
    public void refundPoints(PointRefundCommand command) {
        try {
            stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                    .refundPoints(PointRefundRequest.newBuilder()
                            .setMemberId(command.memberId())
                            .setAmount(command.amount())
                            .setPartyId(command.partyId())
                            .setParticipantId(command.participantId())
                            .setRequestId(command.requestId())
                            .build());
        } catch (StatusRuntimeException e) {
            throw translateException("member gRPC refund failure", e);
        }
    }

    @Override
    public void creditPoints(PointCreditCommand command) {
        try {
            stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                    .creditPoints(PointCreditRequest.newBuilder()
                            .setMemberId(command.memberId())
                            .setAmount(command.amount())
                            .setPartyId(command.partyId())
                            .setRequestId(command.requestId())
                            .build());
        } catch (StatusRuntimeException e) {
            throw translateException("member gRPC credit failure", e);
        }
    }

    private String toApiRole(MemberRole role) {
        return switch (role) {
            case MEMBER_ROLE_DRIVER -> "DRIVER";
            case MEMBER_ROLE_PASSENGER -> "PASSENGER";
            case MEMBER_ROLE_UNSPECIFIED, UNRECOGNIZED ->
                    throw new NonRetryableMemberClientException("member gRPC role lookup returned unsupported role: " + role, null);
        };
    }

    private RuntimeException translateException(String prefix, StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.FAILED_PRECONDITION) {
            return new InsufficientPointsMemberClientException(prefix + ": " + e.getStatus().getCode(), e);
        }
        if (isRetryable(e.getStatus().getCode())) {
            return new RetryableMemberClientException(prefix + ": " + e.getStatus().getCode(), e);
        }
        return new NonRetryableMemberClientException(prefix + ": " + e.getStatus().getCode(), e);
    }

    private boolean isRetryable(Status.Code code) {
        return code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.UNKNOWN
                || code == Status.Code.RESOURCE_EXHAUSTED
                || code == Status.Code.ABORTED;
    }
}
