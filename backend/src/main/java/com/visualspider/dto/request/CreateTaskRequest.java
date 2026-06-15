package com.visualspider.dto.request;

import java.util.List;

/**
 * 创建爬取任务请求体。DETAIL_ONLY 时 urls 非空;LIST_DETAIL 时 urls 忽略。
 */
public record CreateTaskRequest(Long configId, List<String> urls) {
}