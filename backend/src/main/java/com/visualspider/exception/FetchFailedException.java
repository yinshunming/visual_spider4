package com.visualspider.exception;

public class FetchFailedException extends BusinessException {
    public FetchFailedException(int code, String message) {
        super(code, message);
    }

    public static FetchFailedException unreachable(String reason) {
        return new FetchFailedException(4002, "无法访问目标地址：" + reason);
    }

    public static FetchFailedException tooLarge() {
        return new FetchFailedException(4005, "页面内容超过大小限制");
    }
}
