package com.visualspider.config;

import com.visualspider.service.UrlGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 把 {@code crawl.url-guard.allow-loopback} 配置映射到 {@link UrlGuard} 进程开关。
 * 默认 false(生产安全);e2e / 本地 fixture 场景通过环境变量或 application-test.yml 启用。
 */
@Component
public class UrlGuardConfigRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UrlGuardConfigRunner.class);

    @Value("${crawl.url-guard.allow-loopback:false}")
    private boolean allowLoopback;

    @Override
    public void run(ApplicationArguments args) {
        UrlGuard.setAllowLoopback(allowLoopback);
        log.info("UrlGuard ALLOW_LOOPBACK = {}", allowLoopback);
    }
}