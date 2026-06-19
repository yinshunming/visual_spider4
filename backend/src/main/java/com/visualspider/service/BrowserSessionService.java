package com.visualspider.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.visualspider.exception.BrowserSessionAlreadyActiveException;
import com.visualspider.exception.NavigationException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class BrowserSessionService {

    private static final String HIGHLIGHT_STYLE =
            ".vs-highlight{outline:2px solid #ff4d4f;background:rgba(255,77,79,0.15);}";

    private final int viewportWidth;
    private final int viewportHeight;
    private final double deviceScaleFactor;
    private final int navigationTimeoutMs;
    private final boolean headless;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private String sessionId;
    private String currentUrl;
    private boolean active;
    private boolean playwrightReady;

    public BrowserSessionService(Playwright playwright,
                                 @Value("${playwright.viewport.width:1280}") int viewportWidth,
                                 @Value("${playwright.viewport.height:800}") int viewportHeight,
                                 @Value("${playwright.viewport.device-scale-factor:1}") double deviceScaleFactor,
                                 @Value("${playwright.navigation-timeout-ms:15000}") int navigationTimeoutMs,
                                 @Value("${playwright.headless:true}") boolean headless) {
        this.playwright = playwright;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.deviceScaleFactor = deviceScaleFactor;
        this.navigationTimeoutMs = navigationTimeoutMs;
        this.headless = headless;
    }

    @PostConstruct
    public void init() {
        if (playwright != null) {
            playwrightReady = true;
        }
    }

    @PreDestroy
    void shutdown() {
        close();
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    public String open() {
        if (active) {
            throw new BrowserSessionAlreadyActiveException();
        }
        if (playwright == null) {
            throw new NavigationException("Playwright 未就绪，请先安装 Chromium");
        }
        BrowserType chromium = playwright.chromium();
        browser = chromium.launch(new BrowserType.LaunchOptions().setHeadless(headless));
        sessionId = UUID.randomUUID().toString();
        currentUrl = null;
        active = true;
        return sessionId;
    }

    public void close() {
        if (page != null) {
            try { page.close(); } catch (Exception ignored) {}
            page = null;
        }
        if (context != null) {
            try { context.close(); } catch (Exception ignored) {}
            context = null;
        }
        if (browser != null) {
            try { browser.close(); } catch (Exception ignored) {}
            browser = null;
        }
        active = false;
        currentUrl = null;
    }

    public void load(String url) {
        if (!active) {
            throw new NavigationException("浏览器会话未打开");
        }
        try {
            if (context != null) {
                try { context.close(); } catch (Exception ignored) {}
            }
            context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(viewportWidth, viewportHeight)
                    .setDeviceScaleFactor(deviceScaleFactor));
            page = context.newPage();
            page.setDefaultNavigationTimeout(navigationTimeoutMs);
            page.navigate(url);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            // 样式注入必须在 navigate 之后:navigate 会替换 document,注入到 about:blank
            // 的 <style> 会随旧 document 一起丢失,导致 .vs-highlight 无样式、红框不显示。
            page.addStyleTag(new Page.AddStyleTagOptions().setContent(HIGHLIGHT_STYLE));
            currentUrl = url;
        } catch (NavigationException e) {
            throw e;
        } catch (Exception e) {
            throw new NavigationException("页面导航失败: " + e.getMessage(), e);
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPlaywrightReady() {
        return playwrightReady;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public Page getPage() {
        return page;
    }

    public BrowserContext getContext() {
        return context;
    }

    public Browser getBrowser() {
        return browser;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }
}
