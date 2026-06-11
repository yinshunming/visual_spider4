package com.visualspider.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.visualspider.exception.BrowserSessionAlreadyActiveException;
import com.visualspider.exception.NavigationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BrowserSessionService")
class BrowserSessionServiceTest {

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private BrowserSessionService service;

    @BeforeEach
    void setUp() {
        playwright = mock(Playwright.class);
        browser = mock(Browser.class);
        context = mock(BrowserContext.class);
        page = mock(Page.class);
        service = new BrowserSessionService(playwright, 1280, 800, 1, 15000, true);
        service.init();
    }

    @Nested
    @DisplayName("open / close 生命周期")
    class Lifecycle {

        private com.microsoft.playwright.BrowserType stubChromiumLaunching(Browser returned) {
            com.microsoft.playwright.BrowserType bt = mock(com.microsoft.playwright.BrowserType.class);
            when(playwright.chromium()).thenReturn(bt);
            when(bt.launch(any(com.microsoft.playwright.BrowserType.LaunchOptions.class))).thenReturn(returned);
            return bt;
        }

        @Test
        @DisplayName("首次 open 返回 sessionId 且 isActive=true")
        void openFirstTimeReturnsSessionIdAndActive() {
            stubChromiumLaunching(browser);

            String id = service.open();

            assertThat(id).isNotBlank();
            assertThat(service.isActive()).isTrue();
        }

        @Test
        @DisplayName("重复 open 抛 BrowserSessionAlreadyActiveException(409)")
        void openTwiceThrowsAlreadyActive() {
            stubChromiumLaunching(browser);
            service.open();

            assertThatThrownBy(service::open)
                    .isInstanceOf(BrowserSessionAlreadyActiveException.class)
                    .satisfies(e -> assertThat(((BrowserSessionAlreadyActiveException) e).getCode()).isEqualTo(409))
                    .hasMessageContaining("已有活跃会话");
        }

        @Test
        @DisplayName("close 后 isActive=false 且可再次 open")
        void closeThenReopen() {
            stubChromiumLaunching(browser);
            String first = service.open();
            service.close();

            assertThat(service.isActive()).isFalse();
            String second = service.open();
            assertThat(second).isNotBlank();
        }
    }

    @Nested
    @DisplayName("load")
    class Load {

        private com.microsoft.playwright.BrowserType stubLaunchAndContext() {
            com.microsoft.playwright.BrowserType bt = mock(com.microsoft.playwright.BrowserType.class);
            when(playwright.chromium()).thenReturn(bt);
            when(bt.launch(any(com.microsoft.playwright.BrowserType.LaunchOptions.class))).thenReturn(browser);
            when(browser.newContext(any(com.microsoft.playwright.Browser.NewContextOptions.class))).thenReturn(context);
            when(context.newPage()).thenReturn(page);
            return bt;
        }

        @Test
        @DisplayName("首次 load 创建 context + page + navigate")
        void loadFirstTimeCreatesContextAndPage() {
            stubLaunchAndContext();
            service.open();

            service.load("https://example.com");

            verify(context).newPage();
            verify(page).navigate("https://example.com");
            assertThat(service.getCurrentUrl()).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("已有 page 时 load 关闭旧 page 并开新 page")
        void loadReplacesPage() {
            stubLaunchAndContext();
            service.open();
            service.load("https://example.com");

            Page newPage = mock(Page.class);
            when(context.newPage()).thenReturn(newPage);
            service.load("https://other.com");

            verify(newPage).navigate("https://other.com");
            assertThat(service.getCurrentUrl()).isEqualTo("https://other.com");
        }

        @Test
        @DisplayName("navigate 抛错时包装为 NavigationException")
        void loadWrapsNavigationError() {
            stubLaunchAndContext();
            service.open();
            doThrow(new RuntimeException("navigate failed"))
                    .when(page).navigate(anyString());

            assertThatThrownBy(() -> service.load("https://broken.example"))
                    .isInstanceOf(NavigationException.class)
                    .hasMessageContaining("navigate failed");
        }
    }
}
