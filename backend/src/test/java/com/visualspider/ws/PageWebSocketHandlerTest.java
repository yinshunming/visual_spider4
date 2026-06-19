package com.visualspider.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visualspider.dto.response.ExtractionPreviewResponse;
import com.visualspider.dto.ws.WsMessage;
import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlField;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;
import com.visualspider.exception.BusinessException;
import com.visualspider.service.BrowserSessionService;
import com.visualspider.service.CrawlFieldService;
import com.visualspider.service.ExtractionService;
import com.visualspider.service.SelectorCraftService;
import com.visualspider.service.SelectorHighlighter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PageWebSocketHandlerTest {

    private BrowserSessionService browserService;
    private SelectorCraftService selectorService;
    private SelectorHighlighter highlighter;
    private CrawlFieldService fieldService;
    private ExtractionService extractionService;
    private PageWebSocketHandler handler;
    private WebSocketSession session;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        browserService = mock(BrowserSessionService.class);
        selectorService = mock(SelectorCraftService.class);
        highlighter = mock(SelectorHighlighter.class);
        fieldService = mock(CrawlFieldService.class);
        extractionService = mock(ExtractionService.class);
        handler = new PageWebSocketHandler(browserService, selectorService, highlighter, fieldService, extractionService);
        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session");
        when(session.isOpen()).thenReturn(true);
    }

    @Test
    @DisplayName("load 消息调 browserService.load 并推 state=LOADED + screenshot")
    void loadDispatchesAndPushes() throws Exception {
        com.microsoft.playwright.Page page = mock(com.microsoft.playwright.Page.class);
        when(browserService.getPage()).thenReturn(page);
        when(highlighter.screenshotBytes()).thenReturn(new byte[]{1, 2, 3});
        String json = mapper.writeValueAsString(new WsMessage<>("load", java.util.Map.of("url", "https://example.com", "configId", 1)));
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(json));

        verify(browserService).load("https://example.com");
        verify(session, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("click 消息: page.evaluate 被调,返回 error 或 selectors")
    void clickDispatchesAndPushes() throws Exception {
        com.microsoft.playwright.Page page = mock(com.microsoft.playwright.Page.class);
        when(browserService.getPage()).thenReturn(page);
        doReturn(null).when(page).evaluate(anyString(), any());

        String json = mapper.writeValueAsString(new WsMessage<>("click", java.util.Map.of("x", 10, "y", 20)));
        handler.handleTextMessage(session, new TextMessage(json));

        verify(page, atLeastOnce()).evaluate(anyString(), any());
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("click 未命中元素推 error NO_ELEMENT")
    void clickNoElement() throws Exception {
        com.microsoft.playwright.Page page = mock(com.microsoft.playwright.Page.class);
        when(browserService.getPage()).thenReturn(page);
        when(page.evaluate(anyString(), any(Object[].class))).thenReturn(null);

        String json = mapper.writeValueAsString(new WsMessage<>("click", java.util.Map.of("x", -1, "y", -1)));
        handler.handleTextMessage(session, new TextMessage(json));

        verify(selectorService, never()).craft(any(), any());
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("scroll 消息调 page.evaluate scrollBy 并推 screenshot")
    void scrollDispatches() throws Exception {
        com.microsoft.playwright.Page page = mock(com.microsoft.playwright.Page.class);
        when(browserService.getPage()).thenReturn(page);
        when(highlighter.screenshotBytes()).thenReturn(new byte[]{1, 2, 3});

        String json = mapper.writeValueAsString(new WsMessage<>("scroll", java.util.Map.of("dy", 600)));
        handler.handleTextMessage(session, new TextMessage(json));

        verify(page).evaluate(contains("scrollBy"), eq(600));
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("scroll 无 Page → 推 error NO_SESSION,不调 evaluate")
    void scrollNoSession() throws Exception {
        when(browserService.getPage()).thenReturn(null);

        String json = mapper.writeValueAsString(new WsMessage<>("scroll", java.util.Map.of("dy", 100)));
        handler.handleTextMessage(session, new TextMessage(json));

        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("saveField 消息调 fieldService.addField 并推 saveFieldResult")
    void saveFieldDispatches() throws Exception {
        CrawlField saved = new CrawlField();
        saved.setId(99L);
        when(fieldService.addField(eq(1L), any(CrawlField.class))).thenReturn(saved);

        String json = mapper.writeValueAsString(new WsMessage<>(
                "saveField",
                java.util.Map.of(
                        "pageType", "LIST",
                        "fieldName", "title",
                        "fieldType", "TEXT",
                        "selector", "div.title")));
        handler.afterConnectionEstablished(session);
        sessionToConfigHack(1L);
        handler.handleTextMessage(session, new TextMessage(json));

        verify(fieldService).addField(eq(1L), any(CrawlField.class));
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("saveField 业务异常转 saveFieldResult.ok=false")
    void saveFieldBusinessError() throws Exception {
        when(fieldService.addField(eq(1L), any(CrawlField.class)))
                .thenThrow(new BusinessException(400, "字段名已存在"));

        String json = mapper.writeValueAsString(new WsMessage<>(
                "saveField",
                java.util.Map.of(
                        "pageType", "LIST",
                        "fieldName", "title",
                        "fieldType", "TEXT",
                        "selector", "div.title")));
        handler.afterConnectionEstablished(session);
        sessionToConfigHack(1L);
        handler.handleTextMessage(session, new TextMessage(json));

        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("close 消息关闭会话并推 state=CLOSED")
    void closeDispatches() throws Exception {
        String json = mapper.writeValueAsString(new WsMessage<>("close", null));
        handler.handleTextMessage(session, new TextMessage(json));

        verify(browserService).close();
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("afterConnectionEstablished 与 afterConnectionClosed 注册/清理 sessionId")
    void lifecycle() {
        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, null);
    }

    @Test
    @DisplayName("previewTemplate + 当前无 Page → 推 error NO_SESSION,不调 ExtractionService")
    void previewTemplateNoPage() throws Exception {
        when(browserService.getPage()).thenReturn(null);

        String json = mapper.writeValueAsString(new WsMessage<>(
                "previewTemplate", java.util.Map.of("pageType", "LIST")));
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(json));

        verify(extractionService, never()).extractByTemplate(any(), any(), any());
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("previewTemplate + sessionToConfig 空 → 推 error BAD_REQUEST")
    void previewTemplateNoConfig() throws Exception {
        com.microsoft.playwright.Page page = mock(com.microsoft.playwright.Page.class);
        when(browserService.getPage()).thenReturn(page);

        String json = mapper.writeValueAsString(new WsMessage<>(
                "previewTemplate", java.util.Map.of("pageType", "LIST")));
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(json));

        verify(extractionService, never()).extractByTemplate(any(), any(), any());
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("previewTemplate + 正常上下文 → 调 ExtractionService 并推 previewTemplateResult")
    void previewTemplateHappy() throws Exception {
        com.microsoft.playwright.Page page = mock(com.microsoft.playwright.Page.class);
        when(browserService.getPage()).thenReturn(page);
        ExtractionPreviewResponse stub = new ExtractionPreviewResponse(java.util.List.of(), java.util.List.of());
        when(extractionService.extractByTemplate(eq(page), eq(1L), eq(FieldPageType.LIST))).thenReturn(stub);

        String json = mapper.writeValueAsString(new WsMessage<>(
                "previewTemplate", java.util.Map.of("pageType", "LIST")));
        handler.afterConnectionEstablished(session);
        sessionToConfigHack(1L);
        handler.handleTextMessage(session, new TextMessage(json));

        verify(extractionService).extractByTemplate(page, 1L, FieldPageType.LIST);
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    private void sessionToConfigHack(long configId) throws Exception {
        String json = mapper.writeValueAsString(new WsMessage<>("load", java.util.Map.of("url", "https://example.com", "configId", configId)));
        handler.handleTextMessage(session, new TextMessage(json));
    }

    private static org.mockito.verification.VerificationMode atLeastOnce() {
        return org.mockito.Mockito.atLeastOnce();
    }

    @Test
    @DisplayName("session 已关闭时收到消息:handleTextMessage 不抛 RuntimeException 给 Spring")
    void closedSessionDoesNotThrow() throws Exception {
        when(session.isOpen()).thenReturn(false);
        String json = mapper.writeValueAsString(new WsMessage<>(
                "previewTemplate", java.util.Map.of("pageType", "LIST")));
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(json));
        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("session 在 sendMessage 期间被关闭(抛 IllegalStateException):外层 try-catch 静默接住,不冒 RuntimeException")
    void sessionClosedDuringSend() throws Exception {
        com.microsoft.playwright.Page page = mock(com.microsoft.playwright.Page.class);
        when(browserService.getPage()).thenReturn(page);
        when(session.isOpen()).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("session closed"))
                .when(session).sendMessage(any(TextMessage.class));

        String json = mapper.writeValueAsString(new WsMessage<>(
                "previewTemplate", java.util.Map.of("pageType", "LIST")));
        handler.afterConnectionEstablished(session);
        sessionToConfigHack(1L);
        handler.handleTextMessage(session, new TextMessage(json));
    }
}
