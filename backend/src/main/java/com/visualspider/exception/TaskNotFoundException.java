package com.visualspider.exception;

/**
 * CrawlTask 不存在(code=4041)。
 */
public class TaskNotFoundException extends BusinessException {
    public TaskNotFoundException(Long id) {
        super(4041, "CrawlTask not found: id=" + id);
    }
}