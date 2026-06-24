package com.wanderpool.be.domain;

import com.wanderpool.be.global.common.BaseTimeEntity;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(name = "party")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Party extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 파티 생성자 (Driver) ID — members.id FK */
    @Column(nullable = false)
    private Long driverMemberId;
    /** 파티 제목 */
    @Column(length = 200)
    private String title;
    /** 파티 설명 */
    @Column(columnDefinition = "TEXT")
    private String description;
    // ────────────── 출발지 ──────────────
    @Column(nullable = false, length = 100)
    private String originName;
    @Column(nullable = false)
    private Double originLat;
    @Column(nullable = false)
    private Double originLng;
    // ────────────── 목적지 ──────────────
    @Column(nullable = false, length = 100)
    private String destinationName;
    @Column(nullable = false)
    private Double destLat;
    @Column(nullable = false)
    private Double destLng;
    // ────────────── 시간 ──────────────
    /** 출발 시각 (Driver 직접 입력) */
    @Column(nullable = false)
    private LocalDateTime departureTime;
    /** 도착 시각 (Driver 직접 입력) */
    @Column(nullable = false)
    private LocalDateTime arrivalTime;
    // ────────────── 인원 ──────────────
    /** 최대 탑승 가능 인원 */
    @Column(nullable = false)
    private Integer maxPassengers;
    /** 현재 참여 인원 (승낙 기준) */
    @Column(nullable = false)
    private Integer currentPassengers = 0;
    // ────────────── 상태 ──────────────
    /** 파티 상태: RECRUITING / CLOSED / IN_PROGRESS / COMPLETED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyStatus status = PartyStatus.RECRUITING;
    // ────────────── 연관관계 ──────────────
    /** 경유지 목록 (최대 3곳, orderIndex 순 정렬) */
    @OneToMany(mappedBy = "party", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @BatchSize(size = 100)
    private List<PartyWaypoint> waypoints = new ArrayList<>();
    /** 참여자 목록 */
    @OneToMany(mappedBy = "party", cascade = CascadeType.ALL)
    private List<PartyParticipant> participants = new ArrayList<>();
    // ════════════════════════════════════════
    //  생성 메서드
    // ════════════════════════════════════════
    public static Party create(
            Long driverMemberId,
            String title,
            String description,
            String originName, Double originLat, Double originLng,
            String destinationName, Double destLat, Double destLng,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            Integer maxPassengers
    ) {
        if (maxPassengers == null || maxPassengers <= 0) {
            throw new IllegalArgumentException("maxPassengers must be greater than 0");
        }
        if (departureTime == null || arrivalTime == null || !arrivalTime.isAfter(departureTime)) {
            throw new IllegalArgumentException("Arrival time must be after departure time");
        }
        if (driverMemberId == null) {
            throw new IllegalArgumentException("driverMemberId must not be null");
        }

        Party party = new Party();
        party.driverMemberId = driverMemberId;
        party.title = title;
        party.description = description;
        party.originName = originName;
        party.originLat = originLat;
        party.originLng = originLng;
        party.destinationName = destinationName;
        party.destLat = destLat;
        party.destLng = destLng;
        party.departureTime = departureTime;
        party.arrivalTime = arrivalTime;
        party.maxPassengers = maxPassengers;
        party.currentPassengers = 0;
        party.status = PartyStatus.RECRUITING;
        return party;
    }
    // ════════════════════════════════════════
    //  비즈니스 메서드
    // ════════════════════════════════════════
    /**
     * 경유지 추가 (최대 3곳)
     * @throws PartyException 경유지 3곳 초과 시
     */
    public void addWaypoint(PartyWaypoint waypoint) {
        if (this.waypoints.size() >= 3) {
            throw new PartyException(PartyErrorCode.WAYPOINT_LIMIT_EXCEEDED);
        }
        this.waypoints.add(waypoint);
        waypoint.assignToParty(this);
    }
    /**
     * 참여 승낙 시 인원 +1
     * 정원 도달 시 자동으로 CLOSED 전환
     */
    public void incrementPassengers() {
        // 상태 및 정원 체크 먼저 수행
        if (this.currentPassengers >= this.maxPassengers) {
            throw new PartyException(PartyErrorCode.CAPACITY_EXCEEDED);
        }
        if (this.status != PartyStatus.RECRUITING) {
            throw new PartyException(PartyErrorCode.PARTY_NOT_RECRUITING);
        }

        this.currentPassengers++;

        // 정원에 도달하면 자동으로 마감 상태로 전이
        if (this.currentPassengers.equals(this.maxPassengers)) {
            this.changeStatus(PartyStatus.CLOSED);
        }
    }
    /**
     * 참여 취소 시 인원 -1
     * CLOSED 상태였으면 RECRUITING으로 복원
     */
    public void decrementPassengers() {
        if (this.currentPassengers <= 0) return; // 0명 이하 방어

        this.currentPassengers--;

        // 현재 모집 중(RECRUITING)이나 마감(CLOSED) 상태일 때만 상태 복구
        if (this.status == PartyStatus.CLOSED) {
            this.changeStatus(PartyStatus.RECRUITING); // 직접 변경 대신 메서드 활용
        }
    }
    /**
     * 상태 변경 (정방향만 허용)
     * @throws PartyException 허용되지 않는 전환 시
     */
    public void changeStatus(PartyStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new PartyException(PartyErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = next;
    }
    /** 정원 초과 여부 */
    public boolean isFull() {
        return this.currentPassengers >= this.maxPassengers;
    }
    /** 해당 멤버가 이 파티의 Driver인지 확인 */
    public boolean isDriver(Long memberId) {
        return memberId != null && memberId.equals(this.driverMemberId);
    }
}
