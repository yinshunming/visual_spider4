package com.visualspider.service;

import com.microsoft.playwright.Page;
import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlField;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import com.visualspider.repository.CrawlConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ExtractionService")
class ExtractionServiceTest {

    private Page page;
    private CrawlConfigRepository repository;
    private CrawlConfigService configService;
    private FieldValueValidator validator;
    private ExtractionService service;

    @BeforeEach
    void setUp() {
        page = mock(Page.class);
        repository = mock(CrawlConfigRepository.class);
        configService = new CrawlConfigService(repository);
        validator = new FieldValueValidator();
        service = new ExtractionService(configService, validator);
    }

    private CrawlField field(Long id, String name, FieldType type, FieldPageType pageType, String selector) {
        CrawlField f = new CrawlField();
        f.setId(id);
        f.setFieldName(name);
        f.setFieldType(type);
        f.setPageType(pageType);
        f.setSelector(selector);
        return f;
    }

    private CrawlConfig config(Long id, PageType pageType, List<CrawlField> fields) {
        CrawlConfig c = new CrawlConfig();
        c.setId(id);
        c.setName("cfg");
        c.setPageType(pageType);
        c.setSelectorType(SelectorType.CSS);
        c.setFields(new java.util.ArrayList<>(fields));
        for (CrawlField f : fields) {
            f.setConfig(c);
        }
        return c;
    }

    private void stubConfig(CrawlConfig c) {
        when(repository.findById(c.getId())).thenReturn(Optional.of(c));
        when(repository.findByIdWithFields(c.getId())).thenReturn(Optional.of(c));
    }

    @Nested
    @DisplayName("LazyInit 防御:fields 必须由 Repository 一次性加载")
    class FetchFieldsEagerly {

        @Test
        @DisplayName("extractByTemplate 通过 findByIdWithFields 取数,不走 findById(避免 LazyInitializationException)")
        void usesEagerFetch() {
            CrawlConfig c = config(1L, PageType.DETAIL_ONLY, List.of(
                    field(1L, "title", FieldType.TEXT, FieldPageType.DETAIL, ".title")));
            when(repository.findByIdWithFields(1L)).thenReturn(Optional.of(c));
            doReturn(List.of("Hello")).when(page).evaluate(anyString(), any());

            service.extractByTemplate(page, 1L, FieldPageType.DETAIL);

            verify(repository).findByIdWithFields(eq(1L));
        }
    }

    @Nested
    @DisplayName("空模板")
    class EmptyTemplate {

        @Test
        @DisplayName("无字段 → fields:[] + warning '该模板未定义任何 LIST 字段'")
        void emptyListTemplate() {
            CrawlConfig c = config(1L, PageType.LIST_DETAIL, List.of());
            stubConfig(c);

            var resp = service.extractByTemplate(page, 1L, FieldPageType.LIST);

            assertThat(resp.fields()).isEmpty();
            assertThat(resp.warnings()).anyMatch(w -> w.contains("LIST") && w.contains("未定义任何"));
        }
    }

    @Nested
    @DisplayName("TEXT 字段")
    class TextField {

        @Test
        @DisplayName("命中 1 个非空 → OK,matchCount=1")
        void textHitOne() {
            CrawlConfig c = config(1L, PageType.DETAIL_ONLY, List.of(
                    field(1L, "title", FieldType.TEXT, FieldPageType.DETAIL, ".title")));
            stubConfig(c);
            doReturn(List.of("Hello")).when(page).evaluate(anyString(), any());

            var resp = service.extractByTemplate(page, 1L, FieldPageType.DETAIL);

            assertThat(resp.fields()).hasSize(1);
            var f = resp.fields().get(0);
            assertThat(f.status().name()).isEqualTo("OK");
            assertThat(f.matchCount()).isEqualTo(1);
            assertThat(f.rawValues()).containsExactly("Hello");
            assertThat(f.validatedValues()).containsExactly("Hello");
        }

        @Test
        @DisplayName("命中 5 个 → matchCount=5")
        void textHitFive() {
            CrawlConfig c = config(1L, PageType.DETAIL_ONLY, List.of(
                    field(1L, "title", FieldType.TEXT, FieldPageType.DETAIL, ".title")));
            stubConfig(c);
            doReturn(List.of("a", "b", "c", "d", "e")).when(page).evaluate(anyString(), any());

            var resp = service.extractByTemplate(page, 1L, FieldPageType.DETAIL);

            var f = resp.fields().get(0);
            assertThat(f.matchCount()).isEqualTo(5);
            assertThat(f.status().name()).isEqualTo("OK");
            assertThat(f.rawValues()).hasSize(5);
            assertThat(f.validatedValues()).hasSize(5);
        }

        @Test
        @DisplayName("命中 0 个 → NO_MATCH")
        void textNoMatch() {
            CrawlConfig c = config(1L, PageType.DETAIL_ONLY, List.of(
                    field(1L, "title", FieldType.TEXT, FieldPageType.DETAIL, ".no-such")));
            stubConfig(c);
            doReturn(List.of()).when(page).evaluate(anyString(), any());

            var resp = service.extractByTemplate(page, 1L, FieldPageType.DETAIL);

            var f = resp.fields().get(0);
            assertThat(f.status().name()).isEqualTo("NO_MATCH");
            assertThat(f.matchCount()).isEqualTo(0);
            assertThat(f.rawValues()).isEmpty();
            assertThat(f.validatedValues()).isEmpty();
        }
    }

    @Nested
    @DisplayName("NUMBER 字段")
    class NumberField {

        @Test
        @DisplayName("命中 'abc' → TYPE_MISMATCH + validatedValues=[null]")
        void numberTypeMismatch() {
            CrawlConfig c = config(1L, PageType.DETAIL_ONLY, List.of(
                    field(1L, "price", FieldType.NUMBER, FieldPageType.DETAIL, ".price")));
            stubConfig(c);
            doReturn(List.of("abc")).when(page).evaluate(anyString(), any());

            var resp = service.extractByTemplate(page, 1L, FieldPageType.DETAIL);

            var f = resp.fields().get(0);
            assertThat(f.status().name()).isEqualTo("TYPE_MISMATCH");
            assertThat(f.matchCount()).isEqualTo(1);
            assertThat(f.rawValues()).containsExactly("abc");
            assertThat(f.validatedValues()).containsExactly((String) null);
            assertThat(f.message()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("URL 字段")
    class UrlField {

        @Test
        @DisplayName("命中 a 标签绝对化 URL(浏览器 .href 行为,mock 直接返回绝对值) → OK")
        void urlAbsoluteHref() {
            CrawlConfig c = config(1L, PageType.LIST_DETAIL, List.of(
                    field(1L, "cover", FieldType.URL, FieldPageType.LIST, ".cover")));
            stubConfig(c);
            doReturn(List.of("https://x.com/a.jpg")).when(page).evaluate(anyString(), any());

            var resp = service.extractByTemplate(page, 1L, FieldPageType.LIST);

            var f = resp.fields().get(0);
            assertThat(f.status().name()).isEqualTo("OK");
            assertThat(f.rawValues()).containsExactly("https://x.com/a.jpg");
            assertThat(f.validatedValues()).containsExactly("https://x.com/a.jpg");
        }

        @Test
        @DisplayName("命中非链接元素 → URL 校验失败 → TYPE_MISMATCH")
        void urlNonLink() {
            CrawlConfig c = config(1L, PageType.DETAIL_ONLY, List.of(
                    field(1L, "url", FieldType.URL, FieldPageType.DETAIL, ".span-class")));
            stubConfig(c);
            doReturn(List.of("not a link")).when(page).evaluate(anyString(), any());

            var resp = service.extractByTemplate(page, 1L, FieldPageType.DETAIL);

            var f = resp.fields().get(0);
            assertThat(f.status().name()).isEqualTo("TYPE_MISMATCH");
            assertThat(f.rawValues()).containsExactly("not a link");
            assertThat(f.validatedValues()).containsExactly((String) null);
        }
    }

    @Nested
    @DisplayName("选择器异常")
    class InvalidSelector {

        @Test
        @DisplayName("page.evaluate 抛错 → SELECTOR_INVALID + message 含错误")
        void selectorInvalid() {
            CrawlConfig c = config(1L, PageType.DETAIL_ONLY, List.of(
                    field(1L, "broken", FieldType.TEXT, FieldPageType.DETAIL, ">>>broken<<<")));
            stubConfig(c);
            doThrow(new RuntimeException("SyntaxError: >>>broken<<<"))
                    .when(page).evaluate(anyString(), any());

            var resp = service.extractByTemplate(page, 1L, FieldPageType.DETAIL);

            var f = resp.fields().get(0);
            assertThat(f.status().name()).isEqualTo("SELECTOR_INVALID");
            assertThat(f.matchCount()).isEqualTo(0);
            assertThat(f.rawValues()).isEmpty();
            assertThat(f.validatedValues()).isEmpty();
            assertThat(f.message()).contains("SyntaxError");
        }
    }

    @Nested
    @DisplayName("detail_url 软警告")
    class DetailUrlWarning {

        @Test
        @DisplayName("LIST_DETAIL + LIST 预览 + 缺 detail_url → warning 含 detail_url 文案")
        void listDetailMissingDetailUrl() {
            CrawlConfig c = config(1L, PageType.LIST_DETAIL, List.of(
                    field(1L, "title", FieldType.TEXT, FieldPageType.LIST, ".title")));
            stubConfig(c);
            doReturn(List.of("a")).when(page).evaluate(anyString(), any());

            var resp = service.extractByTemplate(page, 1L, FieldPageType.LIST);

            assertThat(resp.warnings()).anyMatch(w -> w.contains("detail_url"));
        }

        @Test
        @DisplayName("DETAIL_ONLY → 不含 detail_url 警告")
        void detailOnlyNoWarning() {
            CrawlConfig c = config(1L, PageType.DETAIL_ONLY, List.of(
                    field(1L, "title", FieldType.TEXT, FieldPageType.DETAIL, ".title")));
            stubConfig(c);
            doReturn(List.of("a")).when(page).evaluate(anyString(), any());

            var resp = service.extractByTemplate(page, 1L, FieldPageType.DETAIL);

            assertThat(resp.warnings()).noneMatch(w -> w.contains("detail_url"));
        }

        @Test
        @DisplayName("LIST_DETAIL + LIST 已有 detail_url → 不含 detail_url 警告")
        void listDetailHasDetailUrl() {
            CrawlConfig c = config(1L, PageType.LIST_DETAIL, List.of(
                    field(1L, "title", FieldType.TEXT, FieldPageType.LIST, ".title"),
                    field(2L, "detail_url", FieldType.URL, FieldPageType.LIST, "a.link")));
            stubConfig(c);
            doReturn(List.of("a", "https://x.com/x")).when(page).evaluate(anyString(), any());

            var resp = service.extractByTemplate(page, 1L, FieldPageType.LIST);

            assertThat(resp.warnings()).noneMatch(w -> w.contains("detail_url"));
        }
    }
}
