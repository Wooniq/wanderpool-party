package com.wanderpool.be.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
@Entity
@Table(name = "party_waypoint",
        uniqueConstraints = @UniqueConstraint(columnNames = {"party_id", "order_index"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyWaypoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 소속 파티 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;
    /** 경유 순서 (1, 2, 3) */
    @Column(nullable = false)
    private Integer orderIndex;
    /** 경유지 이름 (예: "양재역") */
    @Column(nullable = false, length = 100)
    private String name;
    /** 위도 */
    @Column(nullable = false)
    private Double latitude;
    /** 경도 */
    @Column(nullable = false)
    private Double longitude;
    /**
     * 예상 도착 시각
     * location-service에서 거리 기반으로 산출하여 세팅
     */
    private LocalDateTime estimatedArrival;
    // ════════════════════════════════════════
    //  생성 메서드
    // ════════════════════════════════════════
    public static PartyWaypoint create(
            int orderIndex,
            String name,
            Double latitude,
            Double longitude
    ) {
        // 범위 및 유효성 검증 강화
        if (orderIndex < 1 || orderIndex > 3) {
            throw new IllegalArgumentException("orderIndex must be between 1 and 3");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Waypoint name must not be blank");
        }
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Latitude and longitude must not be null");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Latitude/Longitude out of range");
        }

        PartyWaypoint wp = new PartyWaypoint();
        wp.orderIndex = orderIndex;
        wp.name = name;
        wp.latitude = latitude;
        wp.longitude = longitude;
        return wp;
    }
    // ════════════════════════════════════════
    //  비즈니스 메서드
    // ════════════════════════════════════════
    /** Party와 연관관계 설정 (Party.addWaypoint()에서 호출) */
    public void assignToParty(Party party) {
        this.party = party;
    }
    /** location-service에서 산출된 ETA 업데이트 */
    public void updateEstimatedArrival(LocalDateTime eta) {
        this.estimatedArrival = eta;
    }
}