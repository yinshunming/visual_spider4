package com.visualspider.config;

import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaywrightConfig {

    private Playwright playwright;

    @Bean
    public Playwright playwright() {
        try {
            this.playwright = Playwright.create();
            return this.playwright;
        } catch (Throwable t) {
            printBanner(t);
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    private void printBanner(Throwable t) {
        System.err.println();
        System.err.println("============================================================");
        System.err.println(" Playwright 启动失败：Chromium 未安装或二进制缺失");
        System.err.println("------------------------------------------------------------");
        System.err.println(" 原因: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        System.err.println(" 解决: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI");
        System.err.println("       -D exec.args=\"install chromium\"");
        System.err.println(" 备注: 浏览器相关接口（/api/v1/browser/sessions 与 /api/v1/ws/page）");
        System.err.println("       将返回 503；其它 REST 仍可用。");
        System.err.println("============================================================");
        System.err.println();
    }
}
