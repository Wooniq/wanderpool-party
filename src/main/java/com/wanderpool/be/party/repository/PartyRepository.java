package com.wanderpool.be.party.repository;

import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.domain.Party;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartyRepository extends JpaRepository<Party, Long>, JpaSpecificationExecutor<Party> {

    /** 비관적 쓰기 락으로 Party를 조회한다 (참여 승낙 시 동시성 제어용) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Party p where p.id = :id")
    Optional<Party> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        select p
        from Party p
        where p.driverMemberId = :memberId
        order by
          case when p.status = com.wanderpool.be.domain.PartyStatus.RECRUITING then 0 else 1 end,
          p.departureTime asc
        """)
    List<Party> findCreatedParties(@Param("memberId") Long memberId);

    @Query("""
        select p
        from Party p
        where exists (
            select 1
            from PartyParticipant pp
            where pp.party = p
              and pp.memberId = :memberId
              and pp.status = :status
        )
        order by
          case when p.status = com.wanderpool.be.domain.PartyStatus.RECRUITING then 0 else 1 end,
          p.departureTime asc
        """)
    List<Party> findJoinedParties(
            @Param("memberId") Long memberId,
            @Param("status") ParticipantStatus status
    );
}
