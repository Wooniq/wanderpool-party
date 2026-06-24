package com.wanderpool.be.party.client;

public class InsufficientPointsMemberClientException extends NonRetryableMemberClientException {

    public InsufficientPointsMemberClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
