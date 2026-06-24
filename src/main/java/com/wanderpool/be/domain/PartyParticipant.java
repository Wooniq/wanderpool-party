package com.wanderpool.be.domain;

import com.wanderpool.be.global.common.BaseTimeEntity;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Entity
@Table(name = "party_participant",
        uniqueConstraints = @UniqueConstraint(columnNames = {"party_id", "member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyParticipant extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 소속 파티 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;
    /** 참여자(승객) ID — members.id FK */
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    // ────────────── 탑승 위치 ──────────────
    /** 픽업 지점 이름 */
    @Column(length = 100)
    private String pickupName;
    /** 픽업 지점 위도 */
    private Double pickupLat;
    /** 픽업 지점 경도 */
    private Double pickupLng;
    // ────────────── 하차 위치 ──────────────
    /** 하차 지점 이름 */
    @Column(length = 100)
    private String dropoffName;
    private Double dropoffLat;
    private Double dropoffLng;
    // ────────────── 포인트 ──────────────
    /** 참여 시 차감된 포인트 (취소 시 이 금액 환불) */
    private Integer pointCost;

    /** 참여 생성 전에 실제 포인트 선차감이 완료됐는지 여부 */
    @Column(nullable = false)
    private boolean paymentDebited;
    // ────────────── 상태 및 시간 ──────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantStatus status = ParticipantStatus.PENDING;

    @Column(length = 200)
    private String cancelReason;

    private LocalDateTime cancelledAt;

    // ════════════════════════════════════════
    //  생성 메서드
    // ════════════════════════════════════════
    public static PartyParticipant create(
            Party party,
            Long memberId,
            String pickupName,
            Double pickupLat,
            Double pickupLng,
            String dropoffName,
            Integer pointCost
    ) {
        return create(party, memberId, pickupName, pickupLat, pickupLng, dropoffName, null, null, pointCost, true);
    }

    public static PartyParticipant create(
            Party party,
            Long memberId,
            String pickupName,
            Double pickupLat,
            Double pickupLng,
            String dropoffName,
            Double dropoffLat,
            Double dropoffLng,
            Integer pointCost
    ) {
        return create(
                party,
                memberId,
                pickupName,
                pickupLat,
                pickupLng,
                dropoffName,
                dropoffLat,
                dropoffLng,
                pointCost,
                true
        );
    }

    public static PartyParticipant create(
            Party party,
            Long memberId,
            String pickupName,
            Double pickupLat,
            Double pickupLng,
            String dropoffName,
            Double dropoffLat,
            Double dropoffLng,
            Integer pointCost,
            boolean paymentDebited
    ) {
        // 필수 필드 즉시 검증
        if (party == null) throw new IllegalArgumentException("party must not be null");
        if (memberId == null) throw new IllegalArgumentException("memberId must not be null");

        PartyParticipant p = new PartyParticipant();
        p.party = party;
        p.memberId = memberId;
        p.pickupName = pickupName;
        p.pickupLat = pickupLat;
        p.pickupLng = pickupLng;
        p.dropoffName = dropoffName;
        p.dropoffLat = dropoffLat;
        p.dropoffLng = dropoffLng;
        p.pointCost = pointCost;
        p.paymentDebited = paymentDebited;
        return p;
    }
    // ════════════════════════════════════════
    //  비즈니스 메서드
    // ════════════════════════════════════════
    /** Driver가 참여 승낙 */
    public void accept() {
        if (this.status != ParticipantStatus.PENDING) {
            throw new PartyException(PartyErrorCode.PARTICIPANT_NOT_PENDING);
        }
        this.status = ParticipantStatus.ACCEPTED;
    }
    /** Driver가 참여 거절 */
    public void reject() {
        reject(null);
    }
    /** Driver가 참여 거절 (사유 포함) */
    public void reject(String reason) {
        if (this.status != ParticipantStatus.PENDING) {
            throw new PartyException(PartyErrorCode.PARTICIPANT_NOT_PENDING);
        }
        this.status = ParticipantStatus.REJECTED;
        this.cancelReason = reason;
    }
    /**
     * 참여자 본인이 취소
     * @param reason 취소 사유
     */
    public void cancel(String reason) {
        if (this.status == ParticipantStatus.CANCELLED || this.status == ParticipantStatus.REJECTED) {
            throw new IllegalStateException("Cannot cancel a participant that is already cancelled or rejected");
        }

        this.status = ParticipantStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelReason = reason;
    }
}
