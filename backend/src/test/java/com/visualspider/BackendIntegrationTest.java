package com.visualspider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visualspider.dto.ws.WsMessage;
import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlField;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import com.visualspider.repository.CrawlConfigRepository;
import com.visualspider.service.BrowserSessionService;
import com.visualspider.service.CrawlFieldService;
import com.visualspider.service.ExtractionService;
import com.visualspider.service.SelectorCraftService;
import com.visualspider.service.SelectorHighlighter;
import com.visualspider.ws.PageWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("端到端集成(模拟 Playwright)")
class BackendIntegrationTest {

    private ObjectMapper mapper;
    private com.microsoft.playwright.Page page;
    private BrowserSessionService browserService;
    private CrawlFieldService fieldService;
    private PageWebSocketHandler handler;
    private WebSocketSession session;
    private CrawlConfig config;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        page = mock(com.microsoft.playwright.Page.class);
        com.microsoft.playwright.Playwright pw = mock(com.microsoft.playwright.Playwright.class);
        com.microsoft.playwright.BrowserType bt = mock(com.microsoft.playwright.BrowserType.class);
        com.microsoft.playwright.Browser browser = mock(com.microsoft.playwright.Browser.class);
        com.microsoft.playwright.BrowserContext context = mock(com.microsoft.playwright.BrowserContext.class);
        when(pw.chromium()).thenReturn(bt);
        when(bt.launch(any(com.microsoft.playwright.BrowserType.LaunchOptions.class))).thenReturn(browser);
        when(browser.newContext(any(com.microsoft.playwright.Browser.NewContextOptions.class))).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        browserService = new BrowserSessionService(pw, 1280, 800, 1, 15000, true);
        browserService.init();
        browserService.open();
        browserService.load("https://example.com");
        SelectorCraftService selectorService = new SelectorCraftService();
        SelectorHighlighter highlighter = new SelectorHighlighter(browserService);
        fieldService = mock(CrawlFieldService.class);
        ExtractionService extractionService = mock(ExtractionService.class);
        handler = new PageWebSocketHandler(browserService, selectorService, highlighter, fieldService, extractionService);
        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("int-test");
        config = new CrawlConfig();
        config.setId(1L);
        config.setName("集成测试");
        config.setPageType(PageType.LIST_DETAIL);
        config.setSelectorType(SelectorType.CSS);
        config.setStatus(ConfigStatus.STOPPED);
    }

    @Test
    @DisplayName("打开→load→click→preview→saveField 全链路,最终 createField 被调")
    void fullChainAddsField() throws Exception {
        when(page.evaluate(anyString(), any())).thenReturn("<div class=\"x\">y</div>");
        when(page.evaluate(anyString())).thenReturn("<html><body><div class=\"x\">y</div></body></html>");
        when(page.screenshot(any(com.microsoft.playwright.Page.ScreenshotOptions.class)))
                .thenReturn(new byte[]{1, 2, 3});

        CrawlField saved = new CrawlField();
        saved.setId(99L);
        saved.setConfig(config);
        saved.setFieldName("title");
        saved.setFieldType(FieldType.TEXT);
        saved.setPageType(FieldPageType.LIST);
        saved.setSelector("div.x");
        when(fieldService.addField(any(Long.class), any(CrawlField.class))).thenReturn(saved);

        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(
                mapper.writeValueAsString(new WsMessage<>("load", Map.of("url", "https://example.com", "configId", 1)))));
        handler.handleTextMessage(session, new TextMessage(
                mapper.writeValueAsString(new WsMessage<>("click", Map.of("x", 10, "y", 20)))));
        handler.handleTextMessage(session, new TextMessage(
                mapper.writeValueAsString(new WsMessage<>("preview", Map.of("selectorType", "css", "selector", "div.x")))));
        handler.handleTextMessage(session, new TextMessage(
                mapper.writeValueAsString(new WsMessage<>("saveField",
                        Map.of("pageType", "LIST", "fieldName", "title", "fieldType", "TEXT", "selector", "div.x")))));

        Mockito.verify(fieldService, Mockito.times(1)).addField(any(Long.class), any(CrawlField.class));
        assertThat(true).isTrue();
    }
}
