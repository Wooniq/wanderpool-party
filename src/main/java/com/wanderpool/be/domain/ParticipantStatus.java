package com.wanderpool.be.domain;

public enum ParticipantStatus {
    PENDING,      // 참여 요청 대기
    ACCEPTED,     // Driver 승낙
    REJECTED,     // Driver 거절
    CANCELLED     // 참여자 본인 취소
}