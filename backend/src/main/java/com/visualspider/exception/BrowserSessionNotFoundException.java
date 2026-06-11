package com.visualspider.exception;

public class BrowserSessionNotFoundException extends BusinessException {
    public BrowserSessionNotFoundException(String id) {
        super(404, "Browser session not found: id=" + id);
    }
}
