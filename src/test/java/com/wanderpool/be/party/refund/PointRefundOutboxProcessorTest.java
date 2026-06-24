package com.wanderpool.be.party.refund;

import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.client.NonRetryableMemberClientException;
import com.wanderpool.be.party.client.PointRefundCommand;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointRefundOutboxProcessorTest {

    @Mock
    private PointRefundOutboxRepository outboxRepository;

    @Mock
    private MemberClient memberClient;

    @InjectMocks
    private PointRefundOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processor, "maxAttempts", 2);
        ReflectionTestUtils.setField(processor, "retryDelaySeconds", 30L);
        ReflectionTestUtils.setField(processor, "processingTimeoutSeconds", 120L);
    }

    @Test
    void process_success_marksOutboxSucceeded() {
        PointRefundOutbox outbox = outbox();
        given(outboxRepository.claimReady(any(), any(), any(LocalDateTime.class))).willReturn(1);
        willAnswer(invocation -> {
            outbox.markProcessing();
            return Optional.of(outbox);
        }).given(outboxRepository).findById(1L);
        given(outboxRepository.save(any(PointRefundOutbox.class))).willAnswer(invocation -> invocation.getArgument(0));

        processor.process(1L);

        verify(memberClient).refundPoints(new PointRefundCommand(2L, 1000, 1L, 10L, "party-cancel:1:10"));
        ArgumentCaptor<PointRefundOutbox> captor = ArgumentCaptor.forClass(PointRefundOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PointRefundOutboxStatus.SUCCEEDED);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(captor.getValue().getProcessedAt()).isNotNull();
    }

    @Test
    void process_failureBeforeMaxAttempts_marksRetryable() {
        PointRefundOutbox outbox = outbox();
        given(outboxRepository.claimReady(any(), any(), any(LocalDateTime.class))).willReturn(1);
        willAnswer(invocation -> {
            outbox.markProcessing();
            return Optional.of(outbox);
        }).given(outboxRepository).findById(1L);
        doThrow(new IllegalStateException("member unavailable"))
                .when(memberClient).refundPoints(any(PointRefundCommand.class));

        processor.process(1L);

        ArgumentCaptor<PointRefundOutbox> captor = ArgumentCaptor.forClass(PointRefundOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PointRefundOutboxStatus.FAILED_RETRYABLE);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(captor.getValue().getLastError()).isEqualTo("member unavailable");
    }

    @Test
    void process_failureAtMaxAttempts_marksManualReview() {
        PointRefundOutbox outbox = outbox();
        outbox.markProcessing();
        outbox.markRetryableFailure("first failure", 0);
        given(outboxRepository.claimReady(any(), any(), any(LocalDateTime.class))).willReturn(1);
        willAnswer(invocation -> {
            outbox.markProcessing();
            return Optional.of(outbox);
        }).given(outboxRepository).findById(1L);
        doThrow(new IllegalStateException("member unavailable"))
                .when(memberClient).refundPoints(any(PointRefundCommand.class));

        processor.process(1L);

        ArgumentCaptor<PointRefundOutbox> captor = ArgumentCaptor.forClass(PointRefundOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PointRefundOutboxStatus.FAILED_MANUAL_REVIEW);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(2);
        assertThat(captor.getValue().getProcessedAt()).isNotNull();
    }

    @Test
    void process_nonRetryableFailure_marksManualReviewImmediately() {
        PointRefundOutbox outbox = outbox();
        given(outboxRepository.claimReady(any(), any(), any(LocalDateTime.class))).willReturn(1);
        willAnswer(invocation -> {
            outbox.markProcessing();
            return Optional.of(outbox);
        }).given(outboxRepository).findById(1L);
        doThrow(new NonRetryableMemberClientException("invalid refund request", null))
                .when(memberClient).refundPoints(any(PointRefundCommand.class));

        processor.process(1L);

        ArgumentCaptor<PointRefundOutbox> captor = ArgumentCaptor.forClass(PointRefundOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PointRefundOutboxStatus.FAILED_MANUAL_REVIEW);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(captor.getValue().getLastError()).isEqualTo("invalid refund request");
    }

    @Test
    void process_skipsWhenClaimFails() {
        given(outboxRepository.claimReady(any(), any(), any(LocalDateTime.class))).willReturn(0);

        processor.process(1L);

        verify(outboxRepository).claimReady(any(), any(), any(LocalDateTime.class));
        verifyNoInteractions(memberClient);
        verifyNoMoreInteractions(outboxRepository);
    }

    @Test
    void processReadyRefunds_requeuesStaleProcessingBeforePolling() {
        PointRefundOutbox outbox = outbox();
        when(outboxRepository.findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(java.util.List.of(outbox));
        given(outboxRepository.claimReady(any(), any(), any(LocalDateTime.class))).willReturn(0);

        processor.processReadyRefunds();

        verify(outboxRepository).requeueStaleProcessing(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(outboxRepository).findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(any(), any());
    }

    private PointRefundOutbox outbox() {
        PointRefundOutbox outbox = PointRefundOutbox.from(
                PointRefundCommand.cancelParticipation(2L, 1000, 1L, 10L)
        );
        ReflectionTestUtils.setField(outbox, "id", 1L);
        return outbox;
    }
}
