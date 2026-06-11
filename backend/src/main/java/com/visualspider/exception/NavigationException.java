package com.visualspider.exception;

public class NavigationException extends BusinessException {
    public NavigationException(String message) {
        super(4006, message);
    }

    public NavigationException(String message, Throwable cause) {
        super(4006, message);
        initCause(cause);
    }
}
