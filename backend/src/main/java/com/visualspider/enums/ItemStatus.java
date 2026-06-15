package com.visualspider.enums;

/**
 * 单条抽取项的处理状态(M4 引入),list_item 与 article 共用:
 * - PENDING:已创建但尚未处理
 * - CRAWLED:抽取成功
 * - FAILED:抽取失败(error_message 含原因)
 */
public enum ItemStatus {
    PENDING,
    CRAWLED,
    FAILED
}