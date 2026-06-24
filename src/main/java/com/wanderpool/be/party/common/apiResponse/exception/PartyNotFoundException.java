package com.wanderpool.be.party.common.apiResponse.exception;

import com.wanderpool.be.global.exception.BusinessException;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;

public class PartyNotFoundException extends BusinessException {

    public PartyNotFoundException() {
        super(PartyErrorCode.PARTY_NOT_FOUND);
    }
}
