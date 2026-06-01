package com.visualspider.exception;

public class ConfigNotFoundException extends BusinessException {
    public ConfigNotFoundException(Long id) {
        super(404, "CrawlConfig not found: id=" + id);
    }
}
