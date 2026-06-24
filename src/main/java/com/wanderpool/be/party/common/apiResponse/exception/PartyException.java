package com.wanderpool.be.party.common.apiResponse.exception;

import com.wanderpool.be.global.exception.BusinessException;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;

public class PartyException extends BusinessException {
    public PartyException(PartyErrorCode errorCode) {
        super(errorCode);
    }
}