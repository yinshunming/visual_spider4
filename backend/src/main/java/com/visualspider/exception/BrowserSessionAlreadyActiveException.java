package com.visualspider.exception;

public class BrowserSessionAlreadyActiveException extends BusinessException {
    public BrowserSessionAlreadyActiveException() {
        super(409, "已有活跃会话，请先关闭");
    }
}
