package com.visualspider.enums;

/**
 * DETAIL_ONLY 模式下,用户提供的 detail_url 状态(M4 引入):
 * - PENDING:已创建但尚未访问
 * - CRAWLED:对应 article 抽取成功
 * - FAILED:对应 article 抽取失败
 */
public enum DetailUrlStatus {
    PENDING,
    CRAWLED,
    FAILED
}