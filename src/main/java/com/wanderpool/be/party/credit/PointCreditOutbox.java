package com.wanderpool.be.party.credit;

import com.wanderpool.be.party.client.PointCreditCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "point_credit_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uk_point_credit_outbox_request_id", columnNames = "request_id")
)
public class PointCreditOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_credit_outbox_id")
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false, length = 100)
    private String requestId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "amount", nullable = false, updatable = false)
    private Integer amount;

    @Column(name = "party_id", nullable = false, updatable = false)
    private Long partyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PointCreditOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    protected PointCreditOutbox() {
    }

    private PointCreditOutbox(PointCreditCommand command, LocalDateTime now) {
        this.requestId = command.requestId();
        this.memberId = command.memberId();
        this.amount = command.amount();
        this.partyId = command.partyId();
        this.status = PointCreditOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextRetryAt = now;
    }

    public static PointCreditOutbox from(PointCreditCommand command) {
        return new PointCreditOutbox(command, LocalDateTime.now());
    }

    public PointCreditCommand toCommand() {
        return new PointCreditCommand(memberId, amount, partyId, requestId);
    }

    public void markSucceeded() {
        this.status = PointCreditOutboxStatus.SUCCEEDED;
        this.lastError = null;
        this.processedAt = LocalDateTime.now();
    }

    public void markRetryableFailure(String errorMessage, long retryDelaySeconds) {
        if (retryDelaySeconds < 0) {
            throw new IllegalArgumentException("retryDelaySeconds must be non-negative");
        }
        this.status = PointCreditOutboxStatus.FAILED_RETRYABLE;
        this.lastError = truncate(errorMessage);
        this.nextRetryAt = LocalDateTime.now().plusSeconds(retryDelaySeconds);
    }

    public void markManualReview(String errorMessage) {
        this.status = PointCreditOutboxStatus.FAILED_MANUAL_REVIEW;
        this.lastError = truncate(errorMessage);
        this.processedAt = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (nextRetryAt == null) {
            nextRetryAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
