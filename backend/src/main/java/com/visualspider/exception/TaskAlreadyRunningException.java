package com.visualspider.exception;

/**
 * 已有任务在运行时,新任务创建被拒(code=4090)。
 */
public class TaskAlreadyRunningException extends BusinessException {
    public TaskAlreadyRunningException() {
        super(4090, "已有任务在运行");
    }
}