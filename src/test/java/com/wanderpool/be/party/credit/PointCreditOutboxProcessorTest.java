package com.wanderpool.be.party.credit;

import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.client.NonRetryableMemberClientException;
import com.wanderpool.be.party.client.PointCreditCommand;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointCreditOutboxProcessorTest {

    @Mock
    private PointCreditOutboxRepository outboxRepository;

    @Mock
    private MemberClient memberClient;

    @InjectMocks
    private PointCreditOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processor, "maxAttempts", 2);
        ReflectionTestUtils.setField(processor, "retryDelaySeconds", 30L);
        ReflectionTestUtils.setField(processor, "processingTimeoutSeconds", 120L);
    }

    @Test
    void process_success_marksOutboxSucceeded() {
        PointCreditOutbox outbox = outbox();
        given(outboxRepository.claimReady(any(), any(), any(LocalDateTime.class))).willReturn(1);
        willAnswer(invocation -> Optional.of(outbox)).given(outboxRepository).findById(1L);
        given(outboxRepository.save(any(PointCreditOutbox.class))).willAnswer(invocation -> invocation.getArgument(0));

        processor.process(1L);

        verify(memberClient).creditPoints(new PointCreditCommand(2L, 1000, 1L, "party-complete:1:driver-credit"));
        ArgumentCaptor<PointCreditOutbox> captor = ArgumentCaptor.forClass(PointCreditOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PointCreditOutboxStatus.SUCCEEDED);
    }

    @Test
    void process_nonRetryableFailure_marksManualReviewImmediately() {
        PointCreditOutbox outbox = outbox();
        given(outboxRepository.claimReady(any(), any(), any(LocalDateTime.class))).willReturn(1);
        willAnswer(invocation -> Optional.of(outbox)).given(outboxRepository).findById(1L);
        doThrow(new NonRetryableMemberClientException("invalid credit request", null))
                .when(memberClient).creditPoints(any(PointCreditCommand.class));

        processor.process(1L);

        ArgumentCaptor<PointCreditOutbox> captor = ArgumentCaptor.forClass(PointCreditOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PointCreditOutboxStatus.FAILED_MANUAL_REVIEW);
        assertThat(captor.getValue().getLastError()).isEqualTo("invalid credit request");
    }

    @Test
    void process_failureBeforeMaxAttempts_marksRetryable() {
        PointCreditOutbox outbox = outbox();
        ReflectionTestUtils.setField(outbox, "attemptCount", 1);
        given(outboxRepository.claimReady(any(), any(), any(LocalDateTime.class))).willReturn(1);
        willAnswer(invocation -> Optional.of(outbox)).given(outboxRepository).findById(1L);
        doThrow(new IllegalStateException("member unavailable"))
                .when(memberClient).creditPoints(any(PointCreditCommand.class));

        processor.process(1L);

        ArgumentCaptor<PointCreditOutbox> captor = ArgumentCaptor.forClass(PointCreditOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PointCreditOutboxStatus.FAILED_RETRYABLE);
        assertThat(captor.getValue().getLastError()).isEqualTo("member unavailable");
        assertThat(captor.getValue().getNextRetryAt()).isNotNull();
    }

    @Test
    void process_failureAtMaxAttempts_marksManualReview() {
        PointCreditOutbox outbox = outbox();
        ReflectionTestUtils.setField(outbox, "attemptCount", 2);
        given(outboxRepository.claimReady(any(), any(), any(LocalDateTime.class))).willReturn(1);
        willAnswer(invocation -> Optional.of(outbox)).given(outboxRepository).findById(1L);
        doThrow(new IllegalStateException("member unavailable"))
                .when(memberClient).creditPoints(any(PointCreditCommand.class));

        processor.process(1L);

        ArgumentCaptor<PointCreditOutbox> captor = ArgumentCaptor.forClass(PointCreditOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PointCreditOutboxStatus.FAILED_MANUAL_REVIEW);
        assertThat(captor.getValue().getLastError()).isEqualTo("member unavailable");
        assertThat(captor.getValue().getProcessedAt()).isNotNull();
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
    void processReadyCredits_requeuesStaleProcessingBeforePolling() {
        PointCreditOutbox outbox = outbox();
        when(outboxRepository.findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(java.util.List.of(outbox));
        given(outboxRepository.claimReady(any(), any(), any(LocalDateTime.class))).willReturn(0);

        processor.processReadyCredits();

        verify(outboxRepository).requeueStaleProcessing(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(outboxRepository).findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(any(), any());
    }

    private PointCreditOutbox outbox() {
        PointCreditOutbox outbox = PointCreditOutbox.from(
                PointCreditCommand.completeParty(2L, 1000, 1L)
        );
        ReflectionTestUtils.setField(outbox, "id", 1L);
        return outbox;
    }
}
