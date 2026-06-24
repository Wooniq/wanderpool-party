package com.wanderpool.be.domain;

public enum PartyStatus {
    RECRUITING,     // 모집 중
    CLOSED,         // 마감 (정원 도달)
    IN_PROGRESS,    // 운행 중
    COMPLETED;      // 완료
    /**
     * 허용되는 상태 전환인지 검증
     * - RECRUITING → CLOSED (정원 도달), IN_PROGRESS (바로 출발)
     * - CLOSED → RECRUITING (취소로 인원 감소), IN_PROGRESS (운행 시작)
     * - IN_PROGRESS → COMPLETED (운행 종료)
     * - COMPLETED → 전환 불가
     */
    public boolean canTransitionTo(PartyStatus next) {
        return switch (this) {
            case RECRUITING  -> next == CLOSED || next == IN_PROGRESS;
            case CLOSED      -> next == RECRUITING || next == IN_PROGRESS;
            case IN_PROGRESS -> next == COMPLETED;
            case COMPLETED   -> false;
        };
    }
}