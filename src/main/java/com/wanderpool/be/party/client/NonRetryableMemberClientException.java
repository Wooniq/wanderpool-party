package com.wanderpool.be.party.client;

public class NonRetryableMemberClientException extends RuntimeException {

    public NonRetryableMemberClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
