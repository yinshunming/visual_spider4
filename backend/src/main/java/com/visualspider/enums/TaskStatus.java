package com.visualspider.enums;

/**
 * 爬取任务生命周期状态(M4 引入):
 * - RUNNING:任务被 CrawlEngine 调度执行中
 * - COMPLETED:任务执行结束(可能含部分失败,但顶层流程走完)
 * - FAILED:任务整体异常终止(startUrl 校验失败 / page.goto 顶层异常 / zombie 清理)
 */
public enum TaskStatus {
    RUNNING,
    COMPLETED,
    FAILED
}