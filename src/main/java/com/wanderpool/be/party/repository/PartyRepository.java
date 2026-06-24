package com.wanderpool.be.party.repository;

import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.domain.Party;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartyRepository extends JpaRepository<Party, Long>, JpaSpecificationExecutor<Party> {

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
