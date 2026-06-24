package com.wanderpool.be;

import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.refund.PointRefundOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.wanderpool.be.party.repository.PartyRepository;
import com.wanderpool.be.party.repository.PartyParticipantRepository;

@SpringBootTest(properties = "grpc.server.port=0")
class PartyApplicationTests {

	@MockBean
	PartyRepository partyRepository;

	@MockBean
	PartyParticipantRepository partyParticipantRepository;

	@MockBean
	PointRefundOutboxRepository pointRefundOutboxRepository;

	@MockBean
	MemberClient memberClient;

	@Test
	void contextLoads() {
	}

}
