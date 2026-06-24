package com.wanderpool.be.party.client;

import com.wanderpool.be.grpc.member.MemberPointServiceGrpc;
import com.wanderpool.be.grpc.member.GetMemberRoleResponse;
import com.wanderpool.be.grpc.member.MemberRole;
import com.wanderpool.be.grpc.member.PointCreditResponse;
import com.wanderpool.be.grpc.member.PointDebitResponse;
import com.wanderpool.be.grpc.member.PointRefundResponse;
import io.grpc.Status;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrpcMemberClientTest {

    private static final PointDebitCommand DEBIT_COMMAND = new PointDebitCommand(
            10L,
            1500,
            20L,
            null,
            "party-join:20:10"
    );
    private static final PointCreditCommand CREDIT_COMMAND = new PointCreditCommand(
            10L,
            1500,
            20L,
            "party-complete:20:driver-credit"
    );
    private static final PointRefundCommand COMMAND = new PointRefundCommand(
            10L,
            1500,
            20L,
            30L,
            "party-cancel:20:30"
    );

    private MemberPointServiceGrpc.MemberPointServiceBlockingStub stub;
    private GrpcMemberClient client;

    @BeforeEach
    void setUp() {
        stub = mock(MemberPointServiceGrpc.MemberPointServiceBlockingStub.class);
        client = new GrpcMemberClient();
        ReflectionTestUtils.setField(client, "stub", stub);
        ReflectionTestUtils.setField(client, "timeoutMs", 750L);
        when(stub.withDeadlineAfter(750L, TimeUnit.MILLISECONDS)).thenReturn(stub);
    }

    @Test
    void refundPoints_callsGrpcStubWithCommandFields() {
        when(stub.refundPoints(any())).thenReturn(PointRefundResponse.newBuilder()
                .setMemberId(10L)
                .setRefundedAmount(1500)
                .setCurrentPoint(3000)
                .build());

        client.refundPoints(COMMAND);

        ArgumentCaptor<com.wanderpool.be.grpc.member.PointRefundRequest> requestCaptor =
                ArgumentCaptor.forClass(com.wanderpool.be.grpc.member.PointRefundRequest.class);
        verify(stub).withDeadlineAfter(750L, TimeUnit.MILLISECONDS);
        verify(stub).refundPoints(requestCaptor.capture());

        com.wanderpool.be.grpc.member.PointRefundRequest request = requestCaptor.getValue();
        assertThat(request.getMemberId()).isEqualTo(10L);
        assertThat(request.getAmount()).isEqualTo(1500);
        assertThat(request.getPartyId()).isEqualTo(20L);
        assertThat(request.getParticipantId()).isEqualTo(30L);
        assertThat(request.getRequestId()).isEqualTo("party-cancel:20:30");
    }

    @Test
    void debitPoints_callsGrpcStubWithCommandFields() {
        when(stub.debitPoints(any())).thenReturn(PointDebitResponse.newBuilder()
                .setMemberId(10L)
                .setDebitedAmount(1500)
                .setCurrentPoint(500)
                .build());

        client.debitPoints(DEBIT_COMMAND);

        ArgumentCaptor<com.wanderpool.be.grpc.member.PointDebitRequest> requestCaptor =
                ArgumentCaptor.forClass(com.wanderpool.be.grpc.member.PointDebitRequest.class);
        verify(stub).debitPoints(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getRequestId()).isEqualTo("party-join:20:10");
        assertThat(requestCaptor.getValue().getPartyId()).isEqualTo(20L);
        assertThat(requestCaptor.getValue().hasParticipantId()).isFalse();
    }

    @Test
    void debitPoints_includesParticipantIdWhenProvided() {
        PointDebitCommand command = new PointDebitCommand(
                10L,
                1500,
                20L,
                30L,
                "party-join:20:10"
        );
        when(stub.debitPoints(any())).thenReturn(PointDebitResponse.newBuilder()
                .setMemberId(10L)
                .setDebitedAmount(1500)
                .setCurrentPoint(500)
                .build());

        client.debitPoints(command);

        ArgumentCaptor<com.wanderpool.be.grpc.member.PointDebitRequest> requestCaptor =
                ArgumentCaptor.forClass(com.wanderpool.be.grpc.member.PointDebitRequest.class);
        verify(stub).debitPoints(requestCaptor.capture());
        assertThat(requestCaptor.getValue().hasParticipantId()).isTrue();
        assertThat(requestCaptor.getValue().getParticipantId()).isEqualTo(30L);
    }

    @Test
    void creditPoints_callsGrpcStubWithCommandFields() {
        when(stub.creditPoints(any())).thenReturn(PointCreditResponse.newBuilder()
                .setMemberId(10L)
                .setCreditedAmount(1500)
                .setCurrentPoint(3000)
                .build());

        client.creditPoints(CREDIT_COMMAND);

        ArgumentCaptor<com.wanderpool.be.grpc.member.PointCreditRequest> requestCaptor =
                ArgumentCaptor.forClass(com.wanderpool.be.grpc.member.PointCreditRequest.class);
        verify(stub).creditPoints(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getRequestId()).isEqualTo("party-complete:20:driver-credit");
        assertThat(requestCaptor.getValue().getPartyId()).isEqualTo(20L);
    }

    @Test
    void getRole_returnsDriverRoleFromGrpcStub() {
        when(stub.getMemberRole(any())).thenReturn(GetMemberRoleResponse.newBuilder()
                .setMemberId(10L)
                .setRole(MemberRole.MEMBER_ROLE_DRIVER)
                .build());

        String role = client.getRole(10L);

        ArgumentCaptor<com.wanderpool.be.grpc.member.GetMemberRoleRequest> requestCaptor =
                ArgumentCaptor.forClass(com.wanderpool.be.grpc.member.GetMemberRoleRequest.class);
        verify(stub).getMemberRole(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getMemberId()).isEqualTo(10L);
        assertThat(role).isEqualTo("DRIVER");
    }

    @Test
    void getRole_deadlineExceededThrowsRetryableException() {
        when(stub.getMemberRole(any())).thenThrow(Status.DEADLINE_EXCEEDED.asRuntimeException());

        assertThatThrownBy(() -> client.getRole(10L))
                .isInstanceOf(RetryableMemberClientException.class)
                .hasMessageContaining("DEADLINE_EXCEEDED");
    }

    @Test
    void getRole_unsupportedRoleThrowsNonRetryableException() {
        when(stub.getMemberRole(any())).thenReturn(GetMemberRoleResponse.newBuilder()
                .setMemberId(10L)
                .setRole(MemberRole.MEMBER_ROLE_UNSPECIFIED)
                .build());

        assertThatThrownBy(() -> client.getRole(10L))
                .isInstanceOf(NonRetryableMemberClientException.class)
                .hasMessageContaining("unsupported role");
    }

    @Test
    void refundPoints_unavailableThrowsRetryableException() {
        when(stub.refundPoints(any())).thenThrow(Status.UNAVAILABLE.asRuntimeException());

        assertThatThrownBy(() -> client.refundPoints(COMMAND))
                .isInstanceOf(RetryableMemberClientException.class)
                .hasMessageContaining("UNAVAILABLE");
    }

    @Test
    void refundPoints_invalidArgumentThrowsNonRetryableException() {
        when(stub.refundPoints(any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

        assertThatThrownBy(() -> client.refundPoints(COMMAND))
                .isInstanceOf(NonRetryableMemberClientException.class)
                .hasMessageContaining("INVALID_ARGUMENT");
    }

    @Test
    void debitPoints_failedPreconditionThrowsInsufficientPointsException() {
        when(stub.debitPoints(any())).thenThrow(Status.FAILED_PRECONDITION.asRuntimeException());

        assertThatThrownBy(() -> client.debitPoints(DEBIT_COMMAND))
                .isInstanceOf(InsufficientPointsMemberClientException.class)
                .hasMessageContaining("FAILED_PRECONDITION");
    }

    @Test
    void creditPoints_deadlineExceededThrowsRetryableException() {
        when(stub.creditPoints(any())).thenThrow(Status.DEADLINE_EXCEEDED.asRuntimeException());

        assertThatThrownBy(() -> client.creditPoints(CREDIT_COMMAND))
                .isInstanceOf(RetryableMemberClientException.class)
                .hasMessageContaining("DEADLINE_EXCEEDED");
    }
}
