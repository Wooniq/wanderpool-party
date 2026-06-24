package com.wanderpool.be.party.refund;

public enum PointRefundOutboxStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_MANUAL_REVIEW
}
