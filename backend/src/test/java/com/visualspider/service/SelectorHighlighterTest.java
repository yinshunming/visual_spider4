package com.visualspider.service;

import com.microsoft.playwright.Page;
import com.visualspider.dto.ws.PreviewResultPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SelectorHighlighter")
class SelectorHighlighterTest {

    private BrowserSessionService browserSession;
    private Page page;
    private SelectorHighlighter highlighter;

    @BeforeEach
    void setUp() {
        browserSession = mock(BrowserSessionService.class);
        page = mock(Page.class);
        when(browserSession.getPage()).thenReturn(page);
        highlighter = new SelectorHighlighter(browserSession);
    }

    @Nested
    @DisplayName("highlightAndCount")
    class HighlightAndCount {

        @Test
        @DisplayName("调用 page.evaluate 后返回匹配数")
        void highlightReturnsCount() {
            when(page.evaluate(anyString(), eq("div.item"))).thenReturn(3);

            int count = highlighter.highlightAndCount("div.item");

            assertThat(count).isEqualTo(3);
            ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
            verify(page).evaluate(script.capture(), eq("div.item"));
            assertThat(script.getValue()).contains(".vs-highlight");
            assertThat(script.getValue()).contains("querySelectorAll(sel)");
        }
    }

    @Nested
    @DisplayName("previewResult")
    class PreviewResult {

        @Test
        @DisplayName("使用真实 Jsoup DOM 返回 matchCount + samples")
        void previewReturnsCountAndSamples() {
            when(page.evaluate(anyString())).thenReturn(
                    "<html><body><div class='item'>a</div><div class='item'>b</div></body></html>");

            PreviewResultPayload result = highlighter.previewResult("div.item");

            assertThat(result.matchCount()).isEqualTo(2);
            assertThat(result.samples()).contains("a", "b");
        }
    }
}
