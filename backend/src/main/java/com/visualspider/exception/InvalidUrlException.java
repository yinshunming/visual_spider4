package com.visualspider.exception;

public class InvalidUrlException extends BusinessException {
    public InvalidUrlException(String message) {
        super(4001, message);
    }
}
