package com.visualspider.exception;

public class FetchTimeoutException extends BusinessException {
    public FetchTimeoutException(String message) {
        super(4004, message);
    }
}
