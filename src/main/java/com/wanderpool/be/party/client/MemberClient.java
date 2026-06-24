package com.wanderpool.be.party.client;

public interface MemberClient {

    String getRole(Long memberId);

    void debitPoints(PointDebitCommand command);

    void refundPoints(PointRefundCommand command);

    void creditPoints(PointCreditCommand command);
}
