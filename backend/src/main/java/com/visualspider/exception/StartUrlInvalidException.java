package com.visualspider.exception;

/**
 * CrawlConfig.startUrl 非法(code=4007)。
 * 触发场景:startUrl 为空 / 协议非 http(s) / 指向回环地址。
 */
public class StartUrlInvalidException extends BusinessException {
    public StartUrlInvalidException(String message) {
        super(4007, message);
    }
}