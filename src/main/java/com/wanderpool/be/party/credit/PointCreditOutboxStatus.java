package com.wanderpool.be.party.credit;

public enum PointCreditOutboxStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_MANUAL_REVIEW
}
