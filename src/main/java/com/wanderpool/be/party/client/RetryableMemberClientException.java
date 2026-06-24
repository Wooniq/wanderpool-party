package com.wanderpool.be.party.client;

public class RetryableMemberClientException extends RuntimeException {

    public RetryableMemberClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
