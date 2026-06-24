package com.wanderpool.be.party.common.apiResponse;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanderpool.be.global.code.ErrorCode;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import com.wanderpool.be.party.common.apiResponse.exception.PartyNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PartyApiResponseContractTest {

    @Test
    void partyErrorCodeImplementsCommonErrorCode() {
        // given
        PartyErrorCode errorCode = PartyErrorCode.PARTY_NOT_FOUND;

        // when
        ErrorCode contract = errorCode;

        // then
        assertThat(contract).isInstanceOf(ErrorCode.class);
        assertThat(contract.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(contract.getCode()).isEqualTo("PARTY_404");
    }

    @Test
    void partyNotFoundExceptionExposesPartyErrorCode() {
        // when
        PartyNotFoundException exception = new PartyNotFoundException();

        // then
        assertThat(exception.getErrorCode()).isEqualTo(PartyErrorCode.PARTY_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(PartyErrorCode.PARTY_NOT_FOUND.getMessage());
    }
}
