package com.visualspider.exception;

public class BlockedAddressException extends BusinessException {
    public BlockedAddressException(String message) {
        super(4003, message);
    }
}
